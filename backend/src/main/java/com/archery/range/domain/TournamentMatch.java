package com.archery.range.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 淘汰赛对阵场次。
 *
 * slot_no = round_no * 100 + 轮内序号（如 101、102、201、301），全局唯一且决定树的位置。
 * 首轮 team_a/team_b 直接取签位（team_a_seed/team_b_seed）；后续轮次开始为空，
 * 当 source_slot_a/source_slot_b 指向的比赛确认胜者后，由服务端把胜者写入 team_a_id/team_b_id。
 * 晋级关系存在数据库里，刷新或重启不丢，前端不负责拼接对阵。
 */
@Entity
@Table(name = "tournament_match")
public class TournamentMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @Column(name = "slot_no", nullable = false)
    private Integer slotNo;

    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    @Column(name = "source_slot_a")
    private Integer sourceSlotA;

    @Column(name = "source_slot_b")
    private Integer sourceSlotB;

    @Column(name = "team_a_id")
    private Long teamAId;

    @Column(name = "team_b_id")
    private Long teamBId;

    @Column(name = "team_a_seed")
    private Integer teamASeed;

    @Column(name = "team_b_seed")
    private Integer teamBSeed;

    /** PENDING 待开赛 / LIVE 比赛中 / FINISHED 已结束 */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "winner_team_id")
    private Long winnerTeamId;

    /** REGULATION 局分取胜 / PLAYOFF 加赛取胜 */
    @Column(name = "win_mode", length = 16)
    private String winMode;

    @Column(name = "reg_score_a", nullable = false)
    private Integer regScoreA;

    @Column(name = "reg_score_b", nullable = false)
    private Integer regScoreB;

    @Column(name = "reg_x_a", nullable = false)
    private Integer regXA;

    @Column(name = "reg_x_b", nullable = false)
    private Integer regXB;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(Long tournamentId) {
        this.tournamentId = tournamentId;
    }

    public Integer getSlotNo() {
        return slotNo;
    }

    public void setSlotNo(Integer slotNo) {
        this.slotNo = slotNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getSourceSlotA() {
        return sourceSlotA;
    }

    public void setSourceSlotA(Integer sourceSlotA) {
        this.sourceSlotA = sourceSlotA;
    }

    public Integer getSourceSlotB() {
        return sourceSlotB;
    }

    public void setSourceSlotB(Integer sourceSlotB) {
        this.sourceSlotB = sourceSlotB;
    }

    public Long getTeamAId() {
        return teamAId;
    }

    public void setTeamAId(Long teamAId) {
        this.teamAId = teamAId;
    }

    public Long getTeamBId() {
        return teamBId;
    }

    public void setTeamBId(Long teamBId) {
        this.teamBId = teamBId;
    }

    public Integer getTeamASeed() {
        return teamASeed;
    }

    public void setTeamASeed(Integer teamASeed) {
        this.teamASeed = teamASeed;
    }

    public Integer getTeamBSeed() {
        return teamBSeed;
    }

    public void setTeamBSeed(Integer teamBSeed) {
        this.teamBSeed = teamBSeed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getWinnerTeamId() {
        return winnerTeamId;
    }

    public void setWinnerTeamId(Long winnerTeamId) {
        this.winnerTeamId = winnerTeamId;
    }

    public String getWinMode() {
        return winMode;
    }

    public void setWinMode(String winMode) {
        this.winMode = winMode;
    }

    public Integer getRegScoreA() {
        return regScoreA;
    }

    public void setRegScoreA(Integer regScoreA) {
        this.regScoreA = regScoreA;
    }

    public Integer getRegScoreB() {
        return regScoreB;
    }

    public void setRegScoreB(Integer regScoreB) {
        this.regScoreB = regScoreB;
    }

    public Integer getRegXA() {
        return regXA;
    }

    public void setRegXA(Integer regXA) {
        this.regXA = regXA;
    }

    public Integer getRegXB() {
        return regXB;
    }

    public void setRegXB(Integer regXB) {
        this.regXB = regXB;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
