package com.archery.range.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.archery.range.common.BizException;
import com.archery.range.domain.ArrowScore;
import com.archery.range.domain.Lane;
import com.archery.range.domain.Member;
import com.archery.range.domain.RangeDict;
import com.archery.range.domain.Round;
import com.archery.range.dto.RoundDtos;
import com.archery.range.repository.LaneRepository;
import com.archery.range.repository.MemberRepository;
import com.archery.range.repository.RoundRepository;

/**
 * 计分回合。
 *
 * 本项目的核心数据访问路线在这里落地：Round 持有 {@code List<ArrowScore>}，
 * 用 {@code @OneToMany + @JoinTable + @OrderColumn(name = "shot_index")} 映射成有序集合，
 * 于是「第几支箭」= 列表下标。记一支箭就是往列表尾部 append 后 save，
 * 顺序由 Hibernate 维护的 shot_index 保证，不会乱。
 */
@Service
public class RoundService {

    private static final DateTimeFormatter NO_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RoundRepository roundRepository;
    private final MemberRepository memberRepository;
    private final LaneRepository laneRepository;

    public RoundService(RoundRepository roundRepository, MemberRepository memberRepository, LaneRepository laneRepository) {
        this.roundRepository = roundRepository;
        this.memberRepository = memberRepository;
        this.laneRepository = laneRepository;
    }

    @Transactional(readOnly = true)
    public List<RoundDtos.RoundView> list() {
        return roundRepository.findAllByOrderByStartTimeDesc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<RoundDtos.RoundView> listByMember(Long memberId) {
        return roundRepository.findByMemberIdOrderByStartTimeDesc(memberId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<RoundDtos.RoundView> listByStatus(String status) {
        return roundRepository.findByStatusOrderByStartTimeDesc(status).stream().map(this::toView).toList();
    }

    /** 成绩单：回合 + 按 shot_index 顺序排好的每一支箭 */
    @Transactional(readOnly = true)
    public RoundDtos.RoundDetailView detail(Long id) {
        return toDetail(require(id));
    }

    @Transactional
    public RoundDtos.RoundDetailView start(RoundDtos.RoundStartReq req) {
        Member member = memberRepository.findById(req.memberId())
                .orElseThrow(() -> new BizException("会员不存在：id=" + req.memberId()));
        Lane lane = laneRepository.findByIdForUpdate(req.laneId())
                .orElseThrow(() -> new BizException("箭道不存在：id=" + req.laneId()));
        if ("LOCKED".equals(lane.getStatus())) {
            throw new BizException("箭道 [" + lane.getLaneNo() + "] 处于安全锁定（停射事件未放行），不能开打");
        }
        if ("MAINTENANCE".equals(lane.getStatus())) {
            throw new BizException("箭道 [" + lane.getLaneNo() + "] 维护中，不能开打");
        }
        if (!RangeDict.isValidGroupSize(req.arrowCount())) {
            throw new BizException("一组只能是 6 支或 12 支箭");
        }

        Round round = new Round();
        round.setRoundNo(nextRoundNo());
        round.setMember(member);
        round.setLane(lane);
        round.setStartTime(LocalDateTime.now());
        round.setArrowCount(req.arrowCount());
        round.setTotalScore(0);
        round.setAverageScore(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        round.setPersonalBest(false);
        round.setStatus("ONGOING");
        round.setArrows(new ArrayList<>());

        // 开打即占用该箭道
        if ("OPEN".equals(lane.getStatus())) {
            lane.setStatus("OCCUPIED");
            lane.setOccupantId(member.getId());
            lane.setOccupantName(member.getName());
            lane.setOpenedAt(round.getStartTime());
            lane.setPlannedHours(1);
            laneRepository.save(lane);
        }
        return toDetail(roundRepository.save(round));
    }

    /**
     * 记一支箭：append 到有序集合尾部，再重算总分与平均环。
     * 暂停态（安全联锁）禁止记箭，已记录箭支的顺序与分数原样保留。
     */
    @Transactional
    public RoundDtos.RoundDetailView shoot(Long id, RoundDtos.ShotReq req) {
        Round round = requireForUpdate(id);
        rejectIfPaused(round, "记箭");
        if ("SUBMITTED".equals(round.getStatus())) {
            throw new BizException("回合 [" + round.getRoundNo() + "] 已提交，不能再记箭");
        }
        if (round.getArrows().size() >= round.getArrowCount()) {
            throw new BizException("本组 " + round.getArrowCount() + " 支箭已打满，请先提交回合");
        }
        String ring = req.ring() == null ? "" : req.ring().trim().toUpperCase();
        if (!RangeDict.isValidRing(ring)) {
            throw new BizException("环数只能是 X / 10 / 9 / 8 / 7 / 6 / 5 / 4 / 3 / 2 / 1 / M");
        }
        round.getArrows().add(new ArrowScore(ring, RangeDict.ringValue(ring)));
        recalculate(round);
        return toDetail(roundRepository.save(round));
    }

    /** 撤销最后一支箭（打错环数时回滚一支）。暂停态禁止撤销。 */
    @Transactional
    public RoundDtos.RoundDetailView undo(Long id) {
        Round round = requireForUpdate(id);
        rejectIfPaused(round, "撤销");
        if ("SUBMITTED".equals(round.getStatus())) {
            throw new BizException("回合已提交，不能撤销");
        }
        if (round.getArrows().isEmpty()) {
            throw new BizException("本回合还没有记箭，无需撤销");
        }
        round.getArrows().remove(round.getArrows().size() - 1);
        recalculate(round);
        return toDetail(roundRepository.save(round));
    }

    /**
     * 提交回合：必须打满一组，提交时判定是否个人最好成绩。暂停态禁止提交。
     */
    @Transactional
    public RoundDtos.RoundDetailView submit(Long id) {
        Round round = requireForUpdate(id);
        rejectIfPaused(round, "提交");
        if ("SUBMITTED".equals(round.getStatus())) {
            throw new BizException("回合已提交");
        }
        if (round.getArrows().size() < round.getArrowCount()) {
            throw new BizException("还差 " + (round.getArrowCount() - round.getArrows().size()) + " 支箭未打完，不能提交");
        }
        int historyBest = roundRepository
                .findByMemberIdAndStatusOrderByStartTimeDesc(round.getMember().getId(), "SUBMITTED")
                .stream()
                .filter(item -> !item.getId().equals(round.getId()))
                .mapToInt(item -> item.getTotalScore() == null ? 0 : item.getTotalScore())
                .max()
                .orElse(0);
        round.setPersonalBest(round.getTotalScore() > historyBest);
        round.setStatus("SUBMITTED");
        return toDetail(roundRepository.save(round));
    }

    @Transactional(readOnly = true)
    public Round require(Long id) {
        return roundRepository.findById(id)
                .orElseThrow(() -> new BizException("回合不存在：id=" + id));
    }

    /** 写操作入口：悲观锁回合行，与安全联锁的暂停 / 恢复互斥 */
    private Round requireForUpdate(Long id) {
        return roundRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("回合不存在：id=" + id));
    }

    private static void rejectIfPaused(Round round, String action) {
        if ("PAUSED".equals(round.getStatus())) {
            throw new BizException("回合 [" + round.getRoundNo() + "] 因安全停射事件暂停，放行前不能" + action);
        }
    }

    private void recalculate(Round round) {
        int total = round.getArrows().stream().mapToInt(ArrowScore::getRingValue).sum();
        round.setTotalScore(total);
        if (round.getArrows().isEmpty()) {
            round.setAverageScore(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        } else {
            round.setAverageScore(BigDecimal.valueOf(total)
                    .divide(BigDecimal.valueOf(round.getArrows().size()), 2, RoundingMode.HALF_UP));
        }
    }

    private String nextRoundNo() {
        String no = "R" + LocalDateTime.now().format(NO_STAMP);
        int seq = 1;
        while (roundRepository.existsByRoundNo(no)) {
            no = "R" + LocalDateTime.now().format(NO_STAMP) + seq;
            seq += 1;
        }
        return no;
    }

    private RoundDtos.RoundView toView(Round round) {
        return new RoundDtos.RoundView(
                round.getId(),
                round.getRoundNo(),
                round.getMember() == null ? null : round.getMember().getId(),
                round.getMember() == null ? "" : round.getMember().getName(),
                round.getMember() == null ? "" : round.getMember().getCardNo(),
                round.getLane() == null ? null : round.getLane().getId(),
                round.getLane() == null ? "" : round.getLane().getLaneNo(),
                round.getLane() == null ? null : round.getLane().getDistance(),
                round.getStartTime(),
                round.getArrowCount(),
                round.getArrows().size(),
                round.getTotalScore(),
                round.getAverageScore(),
                round.getPersonalBest(),
                round.getStatus(),
                RangeDict.roundStatusName(round.getStatus()));
    }

    private RoundDtos.RoundDetailView toDetail(Round round) {
        List<RoundDtos.ArrowView> arrows = new ArrayList<>();
        List<ArrowScore> list = round.getArrows();
        for (int index = 0; index < list.size(); index += 1) {
            ArrowScore arrow = list.get(index);
            arrows.add(new RoundDtos.ArrowView(index + 1, arrow.getRing(), arrow.getRingValue()));
        }
        return new RoundDtos.RoundDetailView(toView(round), arrows);
    }
}
