package com.archery.range.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.archery.range.common.BizException;
import com.archery.range.domain.Member;
import com.archery.range.domain.RangeDict;
import com.archery.range.domain.Tournament;
import com.archery.range.domain.TournamentArrow;
import com.archery.range.domain.TournamentGame;
import com.archery.range.domain.TournamentLog;
import com.archery.range.domain.TournamentMatch;
import com.archery.range.domain.TournamentPlayoffRound;
import com.archery.range.domain.TournamentTeam;
import com.archery.range.domain.TournamentTeamMember;
import com.archery.range.dto.TournamentDtos;
import com.archery.range.repository.MemberRepository;
import com.archery.range.repository.TournamentGameRepository;
import com.archery.range.repository.TournamentLogRepository;
import com.archery.range.repository.TournamentMatchRepository;
import com.archery.range.repository.TournamentPlayoffRoundRepository;
import com.archery.range.repository.TournamentRepository;
import com.archery.range.repository.TournamentTeamMemberRepository;
import com.archery.range.repository.TournamentTeamRepository;

/**
 * 馆内团体淘汰赛计分台核心。
 *
 * 几条不可退让的规则：
 * 1. 对阵树由服务端在开赛时落库：首轮按签位挂队，后续轮次场次以 source_slot_a/b 指向前序场次，
 *    每场确认胜者后由服务端把晋级队写入下一场的 team_a/team_b，前端不拼接对阵结果。
 * 2. 赛事一旦 ONGOING，队、人、对阵全部冻结；筹备期经理才可以建队 / 换队 / 删队。
 * 3. 局确认后只读，改错只能撤回【当前未结束比赛的最后一局】，撤回人、时间、原因进完整轨迹。
 * 4. 正规局打平进入加赛：每轮三名队员各一支，先比加赛总分再比 X 数，仍平继续下一轮直至唯一胜者。
 * 5. 角色收口：MANAGER 管赛事与参赛队；REFEREE 推进比赛 / 记分 / 撤回 / 确认胜者；MEMBER 只读。
 */
@Service
public class TournamentService {

    private static final DateTimeFormatter NO_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TournamentRepository tournamentRepository;
    private final TournamentTeamRepository teamRepository;
    private final TournamentTeamMemberRepository memberLinkRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentGameRepository gameRepository;
    private final TournamentPlayoffRoundRepository playoffRepository;
    private final TournamentLogRepository logRepository;
    private final MemberRepository memberRepository;

    public TournamentService(TournamentRepository tournamentRepository,
            TournamentTeamRepository teamRepository,
            TournamentTeamMemberRepository memberLinkRepository,
            TournamentMatchRepository matchRepository,
            TournamentGameRepository gameRepository,
            TournamentPlayoffRoundRepository playoffRepository,
            TournamentLogRepository logRepository,
            MemberRepository memberRepository) {
        this.tournamentRepository = tournamentRepository;
        this.teamRepository = teamRepository;
        this.memberLinkRepository = memberLinkRepository;
        this.matchRepository = matchRepository;
        this.gameRepository = gameRepository;
        this.playoffRepository = playoffRepository;
        this.logRepository = logRepository;
        this.memberRepository = memberRepository;
    }

    // =====================================================================
    // 查询
    // =====================================================================

    @Transactional(readOnly = true)
    public List<TournamentDtos.TournamentSummary> list() {
        return tournamentRepository.findAllByOrderByIdDesc().stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public TournamentDtos.TournamentView detail(Long id) {
        return toView(requireTournament(id));
    }

    @Transactional(readOnly = true)
    public TournamentDtos.BracketView bracket(Long id) {
        Tournament tournament = requireTournament(id);
        Map<Integer, List<TournamentDtos.MatchView>> byRound = new LinkedHashMap<>();
        for (int round = 1; round <= tournament.getTotalRounds(); round += 1) {
            byRound.put(round, new ArrayList<>());
        }
        List<TournamentMatch> matches = matchRepository.findByTournamentIdOrderBySlotNoAsc(id);
        for (TournamentMatch match : matches) {
            byRound.computeIfAbsent(match.getRoundNo(), k -> new ArrayList<>())
                    .add(toMatchView(match, false, false));
        }
        List<TournamentDtos.BracketRound> rounds = byRound.entrySet().stream()
                .map(entry -> new TournamentDtos.BracketRound(entry.getKey(), entry.getValue()))
                .toList();
        return new TournamentDtos.BracketView(id, tournament.getName(), tournament.getStatus(),
                tournament.getCurrentRound(), tournament.getTotalRounds(), rounds);
    }

    @Transactional(readOnly = true)
    public TournamentDtos.MatchView matchDetail(Long id) {
        return toMatchView(requireMatch(id), true, true);
    }

    @Transactional(readOnly = true)
    public List<TournamentDtos.MemberOption> memberOptions() {
        return memberRepository.findAllByOrderByIdAsc().stream()
                .map(m -> new TournamentDtos.MemberOption(m.getId(), m.getName(), m.getCardNo()))
                .toList();
    }

    // =====================================================================
    // 值班经理：建赛事 / 建队换队 / 开赛
    // =====================================================================

    @Transactional
    public TournamentDtos.TournamentView create(TournamentDtos.CreateTournamentReq req) {
        requireManager(req.role(), req.operator(), "建立赛事");
        if (!RangeDict.isValidTeamSize(req.teamSize())) {
            throw new BizException("参赛队数只能是 4 支或 8 支");
        }
        if (req.gamesPerMatch() == null || req.gamesPerMatch() < 1
                || req.gamesPerMatch() > RangeDict.MAX_GAMES_PER_MATCH) {
            throw new BizException("每场局数须在 1 至 " + RangeDict.MAX_GAMES_PER_MATCH + " 局之间");
        }
        if (req.arrowsPerMember() == null || req.arrowsPerMember() < 1
                || req.arrowsPerMember() > RangeDict.MAX_ARROWS_PER_MEMBER) {
            throw new BizException("每局每名队员的规定箭数须在 1 至 " + RangeDict.MAX_ARROWS_PER_MEMBER + " 支之间");
        }
        LocalDateTime now = LocalDateTime.now();
        Tournament tournament = new Tournament();
        tournament.setTournamentNo(nextTournamentNo());
        tournament.setName(req.name().trim());
        tournament.setStatus("SETUP");
        tournament.setTeamSize(req.teamSize());
        tournament.setGamesPerMatch(req.gamesPerMatch());
        tournament.setArrowsPerMember(req.arrowsPerMember());
        tournament.setCurrentRound(1);
        tournament.setTotalRounds(roundsOf(req.teamSize()));
        tournament.setCreatedBy(req.operator().trim());
        tournament.setCreatedAt(now);
        tournament = tournamentRepository.save(tournament);

        // 先把整棵对阵树（含决赛）落成 PENDING 骨架；首轮挂签位，后续轮次记晋级来源。
        createBracketSkeleton(tournament);
        log(tournament.getId(), null, "CREATE", req.operator().trim(), "MANAGER",
                "建立赛事「" + tournament.getName() + "」：" + tournament.getTeamSize() + " 队，每场 "
                        + tournament.getGamesPerMatch() + " 局，每局每人 " + tournament.getArrowsPerMember()
                        + " 支，共 " + tournament.getTotalRounds() + " 轮");
        return toView(tournament);
    }

    @Transactional
    public TournamentDtos.TournamentView upsertTeam(Long tournamentId, TournamentDtos.TeamUpsertReq req) {
        requireManager(req.role(), req.operator(), "管理参赛队");
        Tournament tournament = lockTournament(tournamentId);
        if (!"SETUP".equals(tournament.getStatus())) {
            throw new BizException("赛事已开始，不能再建立或替换队伍");
        }
        if (req.slotNo() == null || req.slotNo() < 1 || req.slotNo() > tournament.getTeamSize()) {
            throw new BizException("签位必须在 1 至 " + tournament.getTeamSize() + " 之间");
        }
        String teamName = req.teamName().trim();

        List<Long> memberIds = req.memberIds() == null ? List.of() : req.memberIds().stream().distinct().toList();
        if (memberIds.size() != RangeDict.MEMBERS_PER_TEAM) {
            throw new BizException("每队必须且只能安排三名不同的现有会员");
        }
        List<Member> members = memberRepository.findAllById(memberIds);
        if (members.size() != RangeDict.MEMBERS_PER_TEAM) {
            throw new BizException("所选会员不存在，请刷新后重新选择");
        }

        // 同赛事会员唯一：本队原成员放行，其他队已占用的会员一律拒绝
        List<TournamentTeam> teams = teamRepository.findByTournamentIdOrderBySlotNoAsc(tournamentId);
        TournamentTeam existing = teams.stream().filter(t -> t.getSlotNo().equals(req.slotNo())).findFirst()
                .orElse(null);
        if (existing == null && teams.stream().anyMatch(t -> t.getTeamName().equals(teamName))) {
            throw new BizException("队名「" + teamName + "」已被其他签位使用");
        }
        if (existing != null && !existing.getTeamName().equals(teamName)
                && teams.stream().anyMatch(t -> t.getTeamName().equals(teamName))) {
            throw new BizException("队名「" + teamName + "」已被其他签位使用");
        }
        Long skipTeamId = existing == null ? null : existing.getId();
        for (Long memberId : memberIds) {
            if (memberLinkRepository.existsByTournamentIdAndMemberId(tournamentId, memberId)) {
                List<TournamentTeamMember> all = memberLinkRepository
                        .findByTournamentIdOrderByTeamIdAscSeatNoAsc(tournamentId);
                boolean mine = all.stream().anyMatch(link -> link.getMemberId().equals(memberId)
                        && skipTeamId != null && link.getTeamId().equals(skipTeamId));
                if (!mine) {
                    Member m = members.stream().filter(x -> x.getId().equals(memberId)).findFirst().orElse(null);
                    throw new BizException("会员「" + (m == null ? memberId : m.getName()) + "」已在其他队伍，同一会员不能同时出现在两支队伍");
                }
            }
        }

        LocalDateTime now = LocalDateTime.now();
        boolean isCreate = existing == null;
        if (isCreate) {
            existing = new TournamentTeam();
            existing.setTournamentId(tournamentId);
            existing.setSlotNo(req.slotNo());
            existing.setCreatedAt(now);
        }
        existing.setTeamName(teamName);
        existing = teamRepository.save(existing);

        // 先删后建本队成员（仅筹备期允许）
        List<TournamentTeamMember> old = memberLinkRepository.findByTeamIdOrderBySeatNoAsc(existing.getId());
        memberLinkRepository.deleteAll(old);
        memberLinkRepository.flush();
        for (int seat = 0; seat < memberIds.size(); seat += 1) {
            TournamentTeamMember link = new TournamentTeamMember();
            link.setTournamentId(tournamentId);
            link.setTeamId(existing.getId());
            link.setMemberId(memberIds.get(seat));
            link.setSeatNo(seat + 1);
            memberLinkRepository.save(link);
        }

        String names = members.stream().map(Member::getName).collect(Collectors.joining("、"));
        log(tournamentId, null, isCreate ? "CREATE_TEAM" : "UPDATE_TEAM", req.operator().trim(), "MANAGER",
                (isCreate ? "建立 " : "调整 ") + "签位 " + req.slotNo() + " 队伍「" + teamName + "」：" + names);
        return toView(tournament);
    }

    @Transactional
    public TournamentDtos.TournamentView removeTeam(Long tournamentId, Integer slotNo,
            TournamentDtos.OperatorRoleReq req) {
        requireManager(req.role(), req.operator(), "删除参赛队");
        Tournament tournament = lockTournament(tournamentId);
        if (!"SETUP".equals(tournament.getStatus())) {
            throw new BizException("赛事已开始，不能再替换或删除队伍");
        }
        TournamentTeam team = teamRepository.findByTournamentIdOrderBySlotNoAsc(tournamentId).stream()
                .filter(t -> t.getSlotNo().equals(slotNo)).findFirst()
                .orElseThrow(() -> new BizException("该签位还没有建立队伍"));
        String teamName = team.getTeamName();
        memberLinkRepository.deleteAll(memberLinkRepository.findByTeamIdOrderBySeatNoAsc(team.getId()));
        teamRepository.delete(team);
        log(tournamentId, null, "REMOVE_TEAM", req.operator().trim(), "MANAGER",
                "删除签位 " + slotNo + " 队伍「" + teamName + "」");
        return toView(tournament);
    }

    @Transactional
    public TournamentDtos.TournamentView start(Long tournamentId, TournamentDtos.OperatorRoleReq req) {
        requireManager(req.role(), req.operator(), "开赛");
        Tournament tournament = lockTournament(tournamentId);
        if ("FINISHED".equals(tournament.getStatus())) {
            throw new BizException("赛事已结束，不能再开赛");
        }
        if ("ONGOING".equals(tournament.getStatus())) {
            throw new BizException("赛事已开始，队伍与对阵不能再替换");
        }
        List<TournamentTeam> teams = teamRepository.findByTournamentIdOrderBySlotNoAsc(tournamentId);
        if (teams.size() != tournament.getTeamSize()) {
            throw new BizException("参赛队伍不齐：当前 " + teams.size() + " 支，需要 " + tournament.getTeamSize()
                    + " 支才能开赛");
        }
        List<TournamentTeamMember> links = memberLinkRepository
                .findByTournamentIdOrderByTeamIdAscSeatNoAsc(tournamentId);
        long distinctMembers = links.stream().map(TournamentTeamMember::getMemberId).distinct().count();
        if (distinctMembers != (long) tournament.getTeamSize() * RangeDict.MEMBERS_PER_TEAM) {
            throw new BizException("每队必须是三名会员，且同一会员不能出现在两支队伍");
        }

        // 首轮对阵：把签位队正式写入 team_a / team_b；后续轮次保持空，等待晋级。
        List<TournamentMatch> firstRound = matchRepository
                .findByTournamentIdAndRoundNoOrderBySlotNoAsc(tournamentId, 1);
        Map<Integer, TournamentTeam> bySlot = teams.stream()
                .collect(Collectors.toMap(TournamentTeam::getSlotNo, Function.identity()));
        for (TournamentMatch match : firstRound) {
            TournamentTeam a = bySlot.get(match.getTeamASeed());
            TournamentTeam b = bySlot.get(match.getTeamBSeed());
            if (a == null || b == null) {
                throw new BizException("第 " + (match.getSlotNo() - 100) + " 场的签位队伍缺失，不能开赛");
            }
            match.setTeamAId(a.getId());
            match.setTeamBId(b.getId());
            matchRepository.save(match);
        }

        LocalDateTime now = LocalDateTime.now();
        tournament.setStatus("ONGOING");
        tournament.setStartedAt(now);
        tournamentRepository.save(tournament);
        log(tournamentId, null, "START_TOURNAMENT", req.operator().trim(), "MANAGER",
                "赛事开始：首轮 " + firstRound.size() + " 场进入待开赛，队伍与对阵关系锁定");
        return toView(tournament);
    }

    // =====================================================================
    // 裁判：推进场次
    // =====================================================================

    @Transactional
    public TournamentDtos.MatchView startMatch(Long matchId, TournamentDtos.OperatorRoleReq req) {
        requireReferee(req.role(), req.operator(), "开赛场次");
        TournamentMatch match = lockMatch(matchId);
        // 已结束的比赛永远不能倒退重开（即使赛事整体已 FINISHED），优先于赛事状态判断
        if ("FINISHED".equals(match.getStatus())) {
            throw new BizException("该场比赛已确认结束，不能倒退重开");
        }
        Tournament tournament = requireTournament(match.getTournamentId());
        if (!"ONGOING".equals(tournament.getStatus())) {
            throw new BizException("赛事尚未开始，不能开赛场次");
        }
        if ("LIVE".equals(match.getStatus())) {
            throw new BizException("该场比赛已经在进行中");
        }
        if (match.getTeamAId() == null || match.getTeamBId() == null) {
            throw new BizException("双方队伍尚未齐（" + describeSource(match) + "），暂不能开赛");
        }
        match.setStatus("LIVE");
        match.setStartedAt(LocalDateTime.now());
        matchRepository.save(match);
        log(match.getTournamentId(), matchId, "START_MATCH", req.operator().trim(), "REFEREE",
                "开赛场次 " + slotLabel(match) + "：" + teamName(match.getTeamAId()) + " VS "
                        + teamName(match.getTeamBId()));
        return toMatchView(match, true, false);
    }

    // ---------------- 局 ----------------

    @Transactional
    public TournamentDtos.MatchView createGame(Long matchId, TournamentDtos.OperatorRoleReq req) {
        requireReferee(req.role(), req.operator(), "新建局");
        TournamentMatch match = lockMatch(matchId);
        requireLive(match);
        List<TournamentGame> games = gameRepository.findByMatchIdOrderByGameNoAsc(matchId);
        if (games.size() >= matchGames(match)) {
            throw new BizException("本场规定的 " + matchGames(match) + " 局已经全部建立");
        }
        // 上一局未确认，不允许开新局（保证按局顺序推进）
        if (!games.isEmpty() && !"CONFIRMED".equals(games.get(games.size() - 1).getStatus())) {
            throw new BizException("第 " + games.get(games.size() - 1).getGameNo() + " 局尚未确认，不能开新局");
        }
        TournamentGame game = new TournamentGame();
        game.setMatchId(matchId);
        game.setGameNo(games.size() + 1);
        game.setStatus("ENTERING");
        game.setScoreA(0);
        game.setScoreB(0);
        game.setXA(0);
        game.setXB(0);
        game.setCreatedBy(req.operator().trim());
        game.setCreatedAt(LocalDateTime.now());
        gameRepository.save(game);
        log(match.getTournamentId(), matchId, "SAVE_GAME", req.operator().trim(), "REFEREE",
                "场次 " + slotLabel(match) + " 进入第 " + game.getGameNo() + " 局记分");
        return toMatchView(match, true, false);
    }

    @Transactional
    public TournamentDtos.MatchView shootGame(Long gameId, TournamentDtos.ArrowReq req) {
        requireReferee(req.role(), req.operator(), "记分");
        TournamentGame game = lockGame(gameId);
        if ("CONFIRMED".equals(game.getStatus())) {
            throw new BizException("比赛已确认（第 " + game.getGameNo() + " 局已确认），不能改分；如确为录入错误，请先撤回最后一局");
        }
        TournamentMatch match = lockMatch(game.getMatchId());
        requireLive(match);
        String ring = req.ring() == null ? "" : req.ring().trim().toUpperCase();
        if (!RangeDict.isValidRing(ring)) {
            throw new BizException("箭值只能是 X / 10 / 9 / 8 / 7 / 6 / 5 / 4 / 3 / 2 / 1 / M");
        }
        Long memberId = req.memberId();
        Map<Long, Long> memberTeam = memberTeamMap(match);
        Long teamId = memberTeam.get(memberId);
        if (teamId == null) {
            throw new BizException("无权记分：该会员不属于当前场次的两支队伍，不能被记入成绩");
        }

        int perMember = matchArrowsPerMember(match);
        List<TournamentArrow> arrows = game.getArrows();
        long memberCount = arrows.stream().filter(a -> a.getMemberId().equals(memberId)).count();
        if (memberCount >= perMember) {
            throw new BizException("队员「" + memberName(memberId) + "」本局规定的 " + perMember + " 支箭已经录齐（箭数未齐时请继续录其他队员）");
        }
        long teamCount = arrows.stream().filter(a -> a.getTeamId().equals(teamId)).count();
        if (teamCount >= (long) perMember * RangeDict.MEMBERS_PER_TEAM) {
            throw new BizException((teamId.equals(match.getTeamAId()) ? "A 队" : "B 队") + "本局箭数已录齐");
        }

        arrows.add(new TournamentArrow(ring, RangeDict.ringValue(ring), memberId, teamId));
        recalcGame(game, match);
        gameRepository.save(game);
        return toMatchView(match, true, false);
    }

    @Transactional
    public TournamentDtos.MatchView confirmGame(Long gameId, TournamentDtos.OperatorRoleReq req) {
        requireReferee(req.role(), req.operator(), "确认局");
        TournamentGame game = lockGame(gameId);
        if ("CONFIRMED".equals(game.getStatus())) {
            throw new BizException("第 " + game.getGameNo() + " 局已经确认，不能重复确认");
        }
        TournamentMatch match = lockMatch(game.getMatchId());
        requireLive(match);
        int required = matchArrowsPerMember(match) * RangeDict.MEMBERS_PER_TEAM * 2;
        if (game.getArrows().size() < required) {
            throw new BizException("箭数未齐：本局须录满 " + required + " 支（两队各三名队员，每人 "
                    + matchArrowsPerMember(match) + " 支），当前 " + game.getArrows().size() + " 支，不能确认");
        }
        game.setStatus("CONFIRMED");
        game.setConfirmedBy(req.operator().trim());
        game.setConfirmedAt(LocalDateTime.now());
        recalcGame(game, match);
        gameRepository.save(game);
        refreshRegTotals(match);
        log(match.getTournamentId(), match.getId(), "CONFIRM_GAME", req.operator().trim(), "REFEREE",
                "确认第 " + game.getGameNo() + " 局：" + teamName(match.getTeamAId()) + " " + game.getScoreA()
                        + " : " + game.getScoreB() + " " + teamName(match.getTeamBId()));
        return toMatchView(match, true, false);
    }

    @Transactional
    public TournamentDtos.MatchView withdrawLastGame(Long matchId, TournamentDtos.WithdrawReq req) {
        requireReferee(req.role(), req.operator(), "撤回最后一局");
        TournamentMatch match = lockMatch(matchId);
        if (!"LIVE".equals(match.getStatus())) {
            throw new BizException("只能撤回当前未结束比赛的局，该场不在比赛中");
        }
        List<TournamentGame> games = gameRepository.findByMatchIdOrderByGameNoAsc(matchId);
        if (games.isEmpty()) {
            throw new BizException("本场还没有录入任何局，无需撤回");
        }
        TournamentGame last = games.get(games.size() - 1);
        int gameNo = last.getGameNo();
        String summary = teamName(match.getTeamAId()) + " " + last.getScoreA() + " : " + last.getScoreB() + " "
                + teamName(match.getTeamBId());
        // 删除整局（级联删箭），撤回后须重新录入
        gameRepository.delete(last);
        gameRepository.flush();
        refreshRegTotals(match);
        log(match.getTournamentId(), matchId, "WITHDRAW_GAME", req.operator().trim(), "REFEREE",
                "撤回第 " + gameNo + " 局（原比分 " + summary + "）。原因：" + req.reason().trim());
        return toMatchView(match, true, false);
    }

    // ---------------- 确认胜者 / 加赛 ----------------

    @Transactional
    public TournamentDtos.MatchView confirmWinner(Long matchId, TournamentDtos.OperatorRoleReq req) {
        requireReferee(req.role(), req.operator(), "确认胜者");
        TournamentMatch match = lockMatch(matchId);
        requireLive(match);
        List<TournamentGame> games = gameRepository.findByMatchIdOrderByGameNoAsc(matchId);
        if (games.size() < matchGames(match)
                || games.stream().anyMatch(g -> !"CONFIRMED".equals(g.getStatus()))) {
            throw new BizException("规定的 " + matchGames(match) + " 局尚未全部确认，不能确认胜者");
        }
        int scoreA = match.getRegScoreA();
        int scoreB = match.getRegScoreB();
        if (scoreA == scoreB) {
            throw new BizException("两队总分相同（" + scoreA + " : " + scoreB + "），须进入加赛箭：每名队员各射一支，先比加赛总分再比 X 数");
        }
        finishMatch(match, scoreA > scoreB ? match.getTeamAId() : match.getTeamBId(), "REGULATION", req.operator());
        return toMatchView(match, true, true);
    }

    @Transactional
    public TournamentDtos.MatchView startPlayoff(Long matchId, TournamentDtos.OperatorRoleReq req) {
        requireReferee(req.role(), req.operator(), "进入加赛");
        TournamentMatch match = lockMatch(matchId);
        requireLive(match);
        List<TournamentGame> games = gameRepository.findByMatchIdOrderByGameNoAsc(matchId);
        if (games.size() < matchGames(match)
                || games.stream().anyMatch(g -> !"CONFIRMED".equals(g.getStatus()))) {
            throw new BizException("正规局未全部确认，不能进入加赛");
        }
        if (!match.getRegScoreA().equals(match.getRegScoreB())) {
            throw new BizException("正规局总分不同，应直接确认胜者，无需加赛");
        }
        List<TournamentPlayoffRound> rounds = playoffRepository.findByMatchIdOrderByRoundNoAsc(matchId);
        if (!rounds.isEmpty() && !"TIED".equals(rounds.get(rounds.size() - 1).getStatus())) {
            throw new BizException("上一轮加赛尚未结束，不能再开新一轮");
        }
        TournamentPlayoffRound round = new TournamentPlayoffRound();
        round.setMatchId(matchId);
        round.setRoundNo(rounds.size() + 1);
        round.setStatus("ENTERING");
        round.setScoreA(0);
        round.setScoreB(0);
        round.setXA(0);
        round.setXB(0);
        round.setCreatedBy(req.operator().trim());
        round.setCreatedAt(LocalDateTime.now());
        playoffRepository.save(round);
        log(match.getTournamentId(), matchId, "START_PLAYOFF", req.operator().trim(), "REFEREE",
                "正规局 " + match.getRegScoreA() + " : " + match.getRegScoreB() + " 平，进入第 "
                        + round.getRoundNo() + " 轮加赛（每名队员各射一支）");
        return toMatchView(match, true, true);
    }

    @Transactional
    public TournamentDtos.MatchView shootPlayoff(Long playoffRoundId, TournamentDtos.ArrowReq req) {
        requireReferee(req.role(), req.operator(), "加赛记分");
        TournamentPlayoffRound round = requirePlayoff(playoffRoundId);
        if (!"ENTERING".equals(round.getStatus())) {
            throw new BizException("本轮加赛已确认，不能改箭；若仍平分请开新一轮加赛");
        }
        TournamentMatch match = lockMatch(round.getMatchId());
        requireLive(match);
        String ring = req.ring() == null ? "" : req.ring().trim().toUpperCase();
        if (!RangeDict.isValidRing(ring)) {
            throw new BizException("箭值只能是 X / 10 / 9 / 8 / 7 / 6 / 5 / 4 / 3 / 2 / 1 / M");
        }
        Map<Long, Long> memberTeam = memberTeamMap(match);
        Long teamId = memberTeam.get(req.memberId());
        if (teamId == null) {
            throw new BizException("无权记分：该会员不属于当前场次的两支队伍，不能被记入加赛成绩");
        }
        List<TournamentArrow> arrows = round.getArrows();
        if (arrows.stream().anyMatch(a -> a.getMemberId().equals(req.memberId()))) {
            throw new BizException("队员「" + memberName(req.memberId()) + "」本轮加赛已射一支，不能重复记录");
        }
        if (arrows.size() >= RangeDict.MEMBERS_PER_TEAM * 2) {
            throw new BizException("本轮加赛 6 支箭已录齐，请确认本轮");
        }
        arrows.add(new TournamentArrow(ring, RangeDict.ringValue(ring), req.memberId(), teamId));
        recalcPlayoff(round, match);
        playoffRepository.save(round);
        return toMatchView(match, false, true);
    }

    @Transactional
    public TournamentDtos.MatchView confirmPlayoff(Long playoffRoundId, TournamentDtos.OperatorRoleReq req) {
        requireReferee(req.role(), req.operator(), "确认加赛轮");
        TournamentPlayoffRound round = requirePlayoff(playoffRoundId);
        if (!"ENTERING".equals(round.getStatus())) {
            throw new BizException("本轮加赛已确认");
        }
        TournamentMatch match = lockMatch(round.getMatchId());
        requireLive(match);
        if (round.getArrows().size() < RangeDict.MEMBERS_PER_TEAM * 2) {
            throw new BizException("箭数未齐：本轮加赛须有 6 支箭（每名队员一支），当前 "
                    + round.getArrows().size() + " 支，不能确认");
        }
        recalcPlayoff(round, match);
        int sA = round.getScoreA();
        int sB = round.getScoreB();
        int xA = round.getXA();
        int xB = round.getXB();
        Long winner;
        if (sA != sB) {
            winner = sA > sB ? match.getTeamAId() : match.getTeamBId();
        } else if (xA != xB) {
            winner = xA > xB ? match.getTeamAId() : match.getTeamBId();
        } else {
            winner = null;
        }

        LocalDateTime now = LocalDateTime.now();
        round.setConfirmedBy(req.operator().trim());
        if (winner == null) {
            // 加赛总分相同、X 数也相同 → 本轮 TIED，等待下一轮加赛
            round.setStatus("TIED");
            round.setDecidedAt(now);
            playoffRepository.save(round);
            log(match.getTournamentId(), match.getId(), "CONFIRM_PLAYOFF", req.operator().trim(), "REFEREE",
                    "第 " + round.getRoundNo() + " 轮加赛仍平（总分 " + sA + ":" + sB + "，X " + xA + ":" + xB
                            + "），继续下一轮加赛");
            return toMatchView(match, false, true);
        }
        round.setStatus("DECIDED");
        round.setWinnerTeamId(winner);
        round.setDecidedAt(now);
        playoffRepository.save(round);
        log(match.getTournamentId(), match.getId(), "CONFIRM_PLAYOFF", req.operator().trim(), "REFEREE",
                "第 " + round.getRoundNo() + " 轮加赛分出胜负（总分 " + sA + ":" + sB + "，X " + xA + ":" + xB
                        + "），胜者「" + teamName(winner) + "」");
        finishMatch(match, winner, "PLAYOFF", req.operator());
        return toMatchView(match, true, true);
    }

    // =====================================================================
    // 内部：对阵树、晋级、收尾
    // =====================================================================

    private void finishMatch(TournamentMatch match, Long winnerTeamId, String winMode, String referee) {
        LocalDateTime now = LocalDateTime.now();
        match.setStatus("FINISHED");
        match.setWinnerTeamId(winnerTeamId);
        match.setWinMode(winMode);
        match.setFinishedAt(now);
        matchRepository.save(match);
        log(match.getTournamentId(), match.getId(), "CONFIRM_WINNER", referee, "REFEREE",
                "场次 " + slotLabel(match) + " 确认胜者「" + teamName(winnerTeamId) + "」（"
                        + RangeDict.winModeName(winMode) + "），比分 " + match.getRegScoreA() + " : "
                        + match.getRegScoreB());

        // 自动晋级：把胜者写入下一场对应侧，绝不依赖前端拼接
        Tournament tournament = lockTournament(match.getTournamentId());
        boolean advanced = false;
        for (TournamentMatch next : matchRepository.findByTournamentIdOrderBySlotNoAsc(tournament.getId())) {
            boolean sideA = match.getSlotNo().equals(next.getSourceSlotA());
            boolean sideB = match.getSlotNo().equals(next.getSourceSlotB());
            if (sideA || sideB) {
                if (sideA) {
                    next.setTeamAId(winnerTeamId);
                } else {
                    next.setTeamBId(winnerTeamId);
                }
                matchRepository.save(next);
                advanced = true;
                log(tournament.getId(), next.getId(), "ADVANCE", "系统", "SYSTEM",
                        "「" + teamName(winnerTeamId) + "」由场次 " + slotLabel(match) + " 自动晋级到场次 "
                                + slotLabel(next) + (sideA ? " 的 A 侧" : " 的 B 侧"));
                break;
            }
        }

        // 轮次推进 / 夺冠
        List<TournamentMatch> roundMatches = matchRepository
                .findByTournamentIdAndRoundNoOrderBySlotNoAsc(tournament.getId(), match.getRoundNo());
        boolean roundDone = roundMatches.stream().allMatch(m -> "FINISHED".equals(m.getStatus()));
        if (roundDone && match.getRoundNo() < tournament.getTotalRounds()) {
            tournament.setCurrentRound(match.getRoundNo() + 1);
            tournamentRepository.save(tournament);
            log(tournament.getId(), null, "ADVANCE", "系统", "SYSTEM",
                    "第 " + match.getRoundNo() + " 轮全部结束，赛事进入第 " + tournament.getCurrentRound() + " 轮");
        }
        boolean isFinal = match.getRoundNo() >= tournament.getTotalRounds();
        if (isFinal) {
            tournament.setStatus("FINISHED");
            tournament.setChampionTeamId(winnerTeamId);
            tournament.setFinishedAt(now);
            tournamentRepository.save(tournament);
            log(tournament.getId(), match.getId(), "CHAMPION", "系统", "SYSTEM",
                    "「" + teamName(winnerTeamId) + "」夺得「" + tournament.getName() + "」冠军");
        } else if (!advanced) {
            // 理论上不会发生：非决赛且没有下一场，说明对阵骨架异常
            throw new BizException("晋级目标场次缺失，对阵关系异常，请联系管理员");
        }
    }

    /** 生成整棵对阵树：首轮按相邻签位配对，后续轮次记录来源槽位。 */
    private void createBracketSkeleton(Tournament tournament) {
        int totalRounds = tournament.getTotalRounds();
        int firstRoundMatches = tournament.getTeamSize() / 2;
        for (int round = 1; round <= totalRounds; round += 1) {
            int matchCount = firstRoundMatches / (1 << (round - 1));
            for (int order = 0; order < matchCount; order += 1) {
                TournamentMatch match = new TournamentMatch();
                match.setTournamentId(tournament.getId());
                match.setRoundNo(round);
                match.setSlotNo(round * 100 + order + 1);
                match.setStatus("PENDING");
                match.setRegScoreA(0);
                match.setRegScoreB(0);
                match.setRegXA(0);
                match.setRegXB(0);
                if (round == 1) {
                    match.setTeamASeed(order * 2 + 1);
                    match.setTeamBSeed(order * 2 + 2);
                } else {
                    int childOrder = order * 2;
                    match.setSourceSlotA((round - 1) * 100 + childOrder + 1);
                    match.setSourceSlotB((round - 1) * 100 + childOrder + 2);
                }
                matchRepository.save(match);
            }
        }
    }

    /** 按本场所属 A/B 队重算一局实时总分与 X 数 */
    private void recalcGame(TournamentGame game, TournamentMatch match) {
        int scoreA = 0;
        int scoreB = 0;
        int xA = 0;
        int xB = 0;
        for (TournamentArrow a : game.getArrows()) {
            int value = a.getRingValue();
            boolean x = "X".equals(a.getRing());
            if (a.getTeamId().equals(match.getTeamAId())) {
                scoreA += value;
                if (x) {
                    xA += 1;
                }
            } else {
                scoreB += value;
                if (x) {
                    xB += 1;
                }
            }
        }
        game.setScoreA(scoreA);
        game.setScoreB(scoreB);
        game.setXA(xA);
        game.setXB(xB);
    }

    /** 按本场所属 A/B 队重算一轮加赛总分与 X 数 */
    private void recalcPlayoff(TournamentPlayoffRound round, TournamentMatch match) {
        int sA = 0;
        int sB = 0;
        int xA = 0;
        int xB = 0;
        for (TournamentArrow a : round.getArrows()) {
            int value = a.getRingValue();
            boolean x = "X".equals(a.getRing());
            if (a.getTeamId().equals(match.getTeamAId())) {
                sA += value;
                if (x) {
                    xA += 1;
                }
            } else {
                sB += value;
                if (x) {
                    xB += 1;
                }
            }
        }
        round.setScoreA(sA);
        round.setScoreB(sB);
        round.setXA(xA);
        round.setXB(xB);
    }

    /** 汇总所有已确认 / 录入中的局到场次正规局总分 */
    private void refreshRegTotals(TournamentMatch match) {
        int scoreA = 0;
        int scoreB = 0;
        int xA = 0;
        int xB = 0;
        for (TournamentGame game : gameRepository.findByMatchIdOrderByGameNoAsc(match.getId())) {
            for (TournamentArrow a : game.getArrows()) {
                boolean isA = a.getTeamId().equals(match.getTeamAId());
                int value = a.getRingValue();
                boolean x = "X".equals(a.getRing());
                if (isA) {
                    scoreA += value;
                    if (x) {
                        xA += 1;
                    }
                } else {
                    scoreB += value;
                    if (x) {
                        xB += 1;
                    }
                }
            }
        }
        match.setRegScoreA(scoreA);
        match.setRegScoreB(scoreB);
        match.setRegXA(xA);
        match.setRegXB(xB);
        matchRepository.save(match);
    }

    // =====================================================================
    // 校验 / 工具
    // =====================================================================

    private Tournament requireTournament(Long id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new BizException("赛事不存在：id=" + id));
    }

    private Tournament lockTournament(Long id) {
        return tournamentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("赛事不存在：id=" + id));
    }

    private TournamentMatch requireMatch(Long id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new BizException("比赛场次不存在：id=" + id));
    }

    private TournamentMatch lockMatch(Long id) {
        return matchRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("比赛场次不存在：id=" + id));
    }

    private TournamentGame lockGame(Long id) {
        return gameRepository.findById(id)
                .orElseThrow(() -> new BizException("局不存在：id=" + id));
    }

    private TournamentPlayoffRound requirePlayoff(Long id) {
        return playoffRepository.findById(id)
                .orElseThrow(() -> new BizException("加赛轮不存在：id=" + id));
    }

    private void requireLive(TournamentMatch match) {
        if (!"LIVE".equals(match.getStatus())) {
            throw new BizException("该场比赛当前为「" + RangeDict.matchStatusName(match.getStatus())
                    + "」，只有比赛中的场次才能记分");
        }
    }

    private void requireManager(String role, String operator, String action) {
        requireOperator(operator);
        if (!"MANAGER".equals(role)) {
            throw new BizException("无权操作：" + action + "仅限值班经理");
        }
    }

    private void requireReferee(String role, String operator, String action) {
        requireOperator(operator);
        if (!"REFEREE".equals(role)) {
            throw new BizException("无权操作：" + action + "仅限裁判");
        }
    }

    private void requireOperator(String operator) {
        if (operator == null || operator.trim().isEmpty()) {
            throw new BizException("请填写操作人姓名");
        }
    }

    private int matchGames(TournamentMatch match) {
        return requireTournament(match.getTournamentId()).getGamesPerMatch();
    }

    private int matchArrowsPerMember(TournamentMatch match) {
        return requireTournament(match.getTournamentId()).getArrowsPerMember();
    }

    /** memberId -> teamId（仅当前对阵双方的六名会员） */
    private Map<Long, Long> memberTeamMap(TournamentMatch match) {
        List<TournamentTeamMember> links = memberLinkRepository
                .findByTournamentIdOrderByTeamIdAscSeatNoAsc(match.getTournamentId());
        Map<Long, Long> map = new LinkedHashMap<>();
        for (TournamentTeamMember link : links) {
            if (link.getTeamId().equals(match.getTeamAId()) || link.getTeamId().equals(match.getTeamBId())) {
                map.put(link.getMemberId(), link.getTeamId());
            }
        }
        return map;
    }

    private void log(Long tournamentId, Long matchId, String action, String operator, String role,
            String detail) {
        TournamentLog entry = new TournamentLog();
        entry.setTournamentId(tournamentId);
        entry.setMatchId(matchId);
        entry.setAction(action);
        entry.setOperator(operator);
        entry.setRole(role);
        entry.setDetail(detail);
        entry.setCreatedAt(LocalDateTime.now());
        logRepository.save(entry);
    }

    private String nextTournamentNo() {
        String no = "T" + LocalDateTime.now().format(NO_STAMP);
        int seq = 1;
        while (tournamentRepository.existsByTournamentNo(no)) {
            no = "T" + LocalDateTime.now().format(NO_STAMP) + seq;
            seq += 1;
        }
        return no;
    }

    private static int roundsOf(int teamSize) {
        int rounds = 0;
        int n = teamSize;
        while (n > 1) {
            n /= 2;
            rounds += 1;
        }
        return rounds;
    }

    private String slotLabel(TournamentMatch match) {
        return "R" + match.getRoundNo() + "-M" + (match.getSlotNo() - match.getRoundNo() * 100);
    }

    private String describeSource(TournamentMatch match) {
        if (match.getTeamAId() != null && match.getTeamBId() != null) {
            return "双方已就位";
        }
        List<String> missing = new ArrayList<>();
        if (match.getTeamAId() == null) {
            missing.add("A 侧等待场次 R" + (match.getRoundNo() - 1) + "-M"
                    + (match.getSourceSlotA() - (match.getRoundNo() - 1) * 100) + " 胜者");
        }
        if (match.getTeamBId() == null) {
            missing.add("B 侧等待场次 R" + (match.getRoundNo() - 1) + "-M"
                    + (match.getSourceSlotB() - (match.getRoundNo() - 1) * 100) + " 胜者");
        }
        return String.join("；", missing);
    }

    private String teamName(Long teamId) {
        if (teamId == null) {
            return "待定";
        }
        return teamRepository.findById(teamId).map(TournamentTeam::getTeamName).orElse("待定");
    }

    private String memberName(Long memberId) {
        if (memberId == null) {
            return "";
        }
        return memberRepository.findById(memberId).map(Member::getName).orElse("#" + memberId);
    }

    // =====================================================================
    // 出参装配
    // =====================================================================

    private TournamentDtos.TeamView toTeamView(TournamentTeam team,
            Map<Long, List<TournamentTeamMember>> membersByTeam, Map<Long, Member> memberMap) {
        List<TournamentDtos.TeamMemberView> members = membersByTeam.getOrDefault(team.getId(), List.of()).stream()
                .sorted(Comparator.comparingInt(TournamentTeamMember::getSeatNo))
                .map(link -> {
                    Member m = memberMap.get(link.getMemberId());
                    return new TournamentDtos.TeamMemberView(link.getMemberId(),
                            m == null ? "#" + link.getMemberId() : m.getName(),
                            m == null ? "" : m.getCardNo(), link.getSeatNo());
                })
                .toList();
        return new TournamentDtos.TeamView(team.getId(), team.getSlotNo(), team.getTeamName(), members);
    }

    private TournamentDtos.MatchView toMatchView(TournamentMatch match, boolean includeGames,
            boolean includePlayoffs) {
        List<TournamentTeam> teams = teamRepository.findByTournamentIdOrderBySlotNoAsc(match.getTournamentId());
        Map<Long, TournamentTeam> teamMap = teams.stream()
                .collect(Collectors.toMap(TournamentTeam::getId, Function.identity()));
        List<TournamentTeamMember> links = memberLinkRepository
                .findByTournamentIdOrderByTeamIdAscSeatNoAsc(match.getTournamentId());
        Map<Long, Member> memberMap = memberRepository
                .findAllById(links.stream().map(TournamentTeamMember::getMemberId).distinct().toList())
                .stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<Long, List<TournamentTeamMember>> membersByTeam = links.stream()
                .collect(Collectors.groupingBy(TournamentTeamMember::getTeamId));

        Tournament tournament = requireTournament(match.getTournamentId());

        List<TournamentDtos.GameView> gameViews = List.of();
        int gameCount = gameRepository.findByMatchIdOrderByGameNoAsc(match.getId()).size();
        if (includeGames) {
            gameViews = gameRepository.findByMatchIdOrderByGameNoAsc(match.getId()).stream()
                    .map(game -> toGameView(game, match, teamMap, membersByTeam, memberMap))
                    .toList();
        }

        List<TournamentDtos.PlayoffRoundView> playoffViews = List.of();
        List<TournamentPlayoffRound> playoffRounds = playoffRepository
                .findByMatchIdOrderByRoundNoAsc(match.getId());
        if (includePlayoffs) {
            playoffViews = playoffRounds.stream()
                    .map(round -> toPlayoffView(round, match, teamMap, membersByTeam, memberMap))
                    .toList();
        }

        TournamentDtos.MatchSide sideA = buildSide(match.getTeamAId(), match.getTeamASeed(),
                teamMap, membersByTeam, memberMap);
        TournamentDtos.MatchSide sideB = buildSide(match.getTeamBId(), match.getTeamBSeed(),
                teamMap, membersByTeam, memberMap);

        List<TournamentMatch> allMatches = matchRepository
                .findByTournamentIdOrderBySlotNoAsc(match.getTournamentId());
        return new TournamentDtos.MatchView(
                match.getId(),
                match.getSlotNo(),
                match.getRoundNo(),
                match.getStatus(),
                RangeDict.matchStatusName(match.getStatus()),
                sideA,
                sideB,
                buildSource(match.getSourceSlotA(), allMatches, teamMap),
                buildSource(match.getSourceSlotB(), allMatches, teamMap),
                match.getWinnerTeamId(),
                match.getWinnerTeamId() == null ? null : teamName(match.getWinnerTeamId()),
                match.getWinMode(),
                RangeDict.winModeName(match.getWinMode()),
                match.getRegScoreA(),
                match.getRegScoreB(),
                match.getRegXA(),
                match.getRegXB(),
                tournament.getGamesPerMatch(),
                tournament.getArrowsPerMember(),
                gameCount,
                playoffRounds.size(),
                match.getStartedAt(),
                match.getFinishedAt(),
                gameViews,
                playoffViews);
    }

    private TournamentDtos.MatchSide buildSide(Long teamId, Integer seed,
            Map<Long, TournamentTeam> teamMap,
            Map<Long, List<TournamentTeamMember>> membersByTeam,
            Map<Long, Member> memberMap) {
        if (teamId == null) {
            return new TournamentDtos.MatchSide(null, null, seed, List.of());
        }
        TournamentTeam team = teamMap.get(teamId);
        if (team == null) {
            return new TournamentDtos.MatchSide(teamId, "队伍#" + teamId, seed, List.of());
        }
        List<TournamentDtos.TeamMemberView> members = membersByTeam.getOrDefault(teamId, List.of()).stream()
                .sorted(Comparator.comparingInt(TournamentTeamMember::getSeatNo))
                .map(link -> {
                    Member m = memberMap.get(link.getMemberId());
                    return new TournamentDtos.TeamMemberView(link.getMemberId(),
                            m == null ? "#" + link.getMemberId() : m.getName(),
                            m == null ? "" : m.getCardNo(), link.getSeatNo());
                })
                .toList();
        return new TournamentDtos.MatchSide(teamId, team.getTeamName(), seed, members);
    }

    private TournamentDtos.SourceInfo buildSource(Integer sourceSlot, List<TournamentMatch> allMatches,
            Map<Long, TournamentTeam> teamMap) {
        if (sourceSlot == null) {
            return null;
        }
        TournamentMatch source = allMatches.stream().filter(m -> m.getSlotNo().equals(sourceSlot)).findFirst()
                .orElse(null);
        if (source == null) {
            return new TournamentDtos.SourceInfo(sourceSlot, null, null, null, null);
        }
        String winnerName = source.getWinnerTeamId() == null ? null : teamName(source.getWinnerTeamId());
        return new TournamentDtos.SourceInfo(sourceSlot, source.getStatus(),
                RangeDict.matchStatusName(source.getStatus()), source.getWinnerTeamId(), winnerName);
    }

    private TournamentDtos.GameView toGameView(TournamentGame game, TournamentMatch match,
            Map<Long, TournamentTeam> teamMap,
            Map<Long, List<TournamentTeamMember>> membersByTeam,
            Map<Long, Member> memberMap) {
        // 重算落库前的实时比分（录入中也要看到当前累计）
        int sA = 0;
        int sB = 0;
        int xA = 0;
        int xB = 0;
        List<TournamentDtos.ArrowView> arrows = new ArrayList<>();
        int order = 0;
        for (TournamentArrow a : game.getArrows()) {
            boolean isA = a.getTeamId().equals(match.getTeamAId());
            if (isA) {
                sA += a.getRingValue();
                if ("X".equals(a.getRing())) {
                    xA += 1;
                }
            } else {
                sB += a.getRingValue();
                if ("X".equals(a.getRing())) {
                    xB += 1;
                }
            }
            order += 1;
            arrows.add(new TournamentDtos.ArrowView(
                    order,
                    a.getMemberId(),
                    memberName(a.getMemberId()),
                    a.getTeamId(),
                    teamName(a.getTeamId()),
                    isA ? "A" : "B",
                    a.getRing(),
                    a.getRingValue()));
        }
        return new TournamentDtos.GameView(
                game.getId(),
                game.getGameNo(),
                game.getStatus(),
                RangeDict.gameStatusName(game.getStatus()),
                sA,
                sB,
                xA,
                xB,
                game.getCreatedBy(),
                game.getConfirmedBy(),
                game.getCreatedAt(),
                game.getConfirmedAt(),
                arrows);
    }

    private TournamentDtos.PlayoffRoundView toPlayoffView(TournamentPlayoffRound round, TournamentMatch match,
            Map<Long, TournamentTeam> teamMap,
            Map<Long, List<TournamentTeamMember>> membersByTeam,
            Map<Long, Member> memberMap) {
        int sA = 0;
        int sB = 0;
        int xA = 0;
        int xB = 0;
        List<TournamentDtos.ArrowView> arrows = new ArrayList<>();
        int order = 0;
        for (TournamentArrow a : round.getArrows()) {
            boolean isA = a.getTeamId().equals(match.getTeamAId());
            if (isA) {
                sA += a.getRingValue();
                if ("X".equals(a.getRing())) {
                    xA += 1;
                }
            } else {
                sB += a.getRingValue();
                if ("X".equals(a.getRing())) {
                    xB += 1;
                }
            }
            order += 1;
            arrows.add(new TournamentDtos.ArrowView(
                    order,
                    a.getMemberId(),
                    memberName(a.getMemberId()),
                    a.getTeamId(),
                    teamName(a.getTeamId()),
                    isA ? "A" : "B",
                    a.getRing(),
                    a.getRingValue()));
        }
        return new TournamentDtos.PlayoffRoundView(
                round.getId(),
                round.getRoundNo(),
                round.getStatus(),
                RangeDict.playoffStatusName(round.getStatus()),
                sA,
                sB,
                xA,
                xB,
                round.getWinnerTeamId(),
                round.getWinnerTeamId() == null ? null : teamName(round.getWinnerTeamId()),
                round.getCreatedBy(),
                round.getConfirmedBy(),
                round.getCreatedAt(),
                round.getDecidedAt(),
                arrows);
    }

    private TournamentDtos.TournamentSummary toSummary(Tournament tournament) {
        Long id = tournament.getId();
        List<TournamentMatch> matches = matchRepository.findByTournamentIdOrderBySlotNoAsc(id);
        int pending = 0;
        int live = 0;
        int finished = 0;
        int playoffMatches = 0;
        for (TournamentMatch m : matches) {
            switch (m.getStatus()) {
                case "PENDING" -> pending += 1;
                case "LIVE" -> live += 1;
                default -> finished += 1;
            }
        }
        List<TournamentPlayoffRound> allPlayoffs = new ArrayList<>();
        for (TournamentMatch m : matches) {
            allPlayoffs.addAll(playoffRepository.findByMatchIdOrderByRoundNoAsc(m.getId()));
        }
        playoffMatches = (int) allPlayoffs.size();
        return new TournamentDtos.TournamentSummary(
                id,
                tournament.getTournamentNo(),
                tournament.getName(),
                tournament.getStatus(),
                RangeDict.tournamentStatusName(tournament.getStatus()),
                tournament.getTeamSize(),
                tournament.getGamesPerMatch(),
                tournament.getArrowsPerMember(),
                tournament.getCurrentRound(),
                tournament.getTotalRounds(),
                tournament.getChampionTeamId(),
                tournament.getChampionTeamId() == null ? null : teamName(tournament.getChampionTeamId()),
                (int) teamRepository.countByTournamentId(id),
                pending,
                live,
                finished,
                playoffMatches,
                tournament.getCreatedAt(),
                tournament.getStartedAt(),
                tournament.getFinishedAt());
    }

    private TournamentDtos.TournamentView toView(Tournament tournament) {
        Long id = tournament.getId();
        List<TournamentTeam> teams = teamRepository.findByTournamentIdOrderBySlotNoAsc(id);
        List<TournamentTeamMember> links = memberLinkRepository
                .findByTournamentIdOrderByTeamIdAscSeatNoAsc(id);
        Map<Long, Member> memberMap = memberRepository
                .findAllById(links.stream().map(TournamentTeamMember::getMemberId).distinct().toList())
                .stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<Long, List<TournamentTeamMember>> membersByTeam = links.stream()
                .collect(Collectors.groupingBy(TournamentTeamMember::getTeamId));

        List<TournamentDtos.TeamView> teamViews = teams.stream()
                .map(team -> toTeamView(team, membersByTeam, memberMap))
                .toList();
        List<TournamentDtos.MatchView> matchViews = matchRepository.findByTournamentIdOrderBySlotNoAsc(id)
                .stream()
                .map(match -> toMatchView(match, false, false))
                .toList();
        List<TournamentDtos.LogView> logs = toLogViews(id);
        return new TournamentDtos.TournamentView(
                id,
                tournament.getTournamentNo(),
                tournament.getName(),
                tournament.getStatus(),
                RangeDict.tournamentStatusName(tournament.getStatus()),
                tournament.getTeamSize(),
                tournament.getGamesPerMatch(),
                tournament.getArrowsPerMember(),
                tournament.getCurrentRound(),
                tournament.getTotalRounds(),
                tournament.getChampionTeamId(),
                tournament.getChampionTeamId() == null ? null : teamName(tournament.getChampionTeamId()),
                tournament.getCreatedBy(),
                tournament.getCreatedAt(),
                tournament.getStartedAt(),
                tournament.getFinishedAt(),
                teamViews,
                matchViews,
                logs);
    }

    private List<TournamentDtos.LogView> toLogViews(Long tournamentId) {
        Map<Long, Integer> slotByMatch = matchRepository.findByTournamentIdOrderBySlotNoAsc(tournamentId).stream()
                .collect(Collectors.toMap(TournamentMatch::getId, TournamentMatch::getSlotNo));
        return logRepository.findByTournamentIdOrderByIdAsc(tournamentId).stream()
                .map(log -> new TournamentDtos.LogView(
                        log.getId(),
                        log.getMatchId(),
                        log.getMatchId() == null ? null : slotByMatch.get(log.getMatchId()),
                        log.getAction(),
                        RangeDict.tournamentActionName(log.getAction()),
                        log.getOperator(),
                        log.getRole(),
                        RangeDict.tournamentRoleName(log.getRole()),
                        log.getDetail(),
                        log.getCreatedAt()))
                .toList();
    }
}
