package com.archery.range.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * 加赛轮：每轮两队各三名队员各射一支（共 6 支）。
 * 判定顺序：先比加赛总分，再比 X 数；仍相同则状态置 TIED，继续下一轮，直到 DECIDED 唯一胜者。
 */
@Entity
@Table(name = "tournament_playoff_round")
public class TournamentPlayoffRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    /** ENTERING 录入中 / TIED 仍平分（继续下一轮）/ DECIDED 已分出胜负 */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "score_a", nullable = false)
    private Integer scoreA;

    @Column(name = "score_b", nullable = false)
    private Integer scoreB;

    @Column(name = "x_a", nullable = false)
    private Integer xA;

    @Column(name = "x_b", nullable = false)
    private Integer xB;

    @Column(name = "winner_team_id")
    private Long winnerTeamId;

    @Column(name = "created_by", length = 32)
    private String createdBy;

    @Column(name = "confirmed_by", length = 32)
    private String confirmedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    // 与 TournamentGame 同款有序集合：连接表 tournament_playoff_arrow，shot_index 为加赛箭序。
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(
            name = "tournament_playoff_arrow",
            joinColumns = @JoinColumn(name = "playoff_round_id"),
            inverseJoinColumns = @JoinColumn(name = "arrow_id"))
    @OrderColumn(name = "shot_index")
    private List<TournamentArrow> arrows = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMatchId() {
        return matchId;
    }

    public void setMatchId(Long matchId) {
        this.matchId = matchId;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getScoreA() {
        return scoreA;
    }

    public void setScoreA(Integer scoreA) {
        this.scoreA = scoreA;
    }

    public Integer getScoreB() {
        return scoreB;
    }

    public void setScoreB(Integer scoreB) {
        this.scoreB = scoreB;
    }

    public Integer getXA() {
        return xA;
    }

    public void setXA(Integer xA) {
        this.xA = xA;
    }

    public Integer getXB() {
        return xB;
    }

    public void setXB(Integer xB) {
        this.xB = xB;
    }

    public Long getWinnerTeamId() {
        return winnerTeamId;
    }

    public void setWinnerTeamId(Long winnerTeamId) {
        this.winnerTeamId = winnerTeamId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(String confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public List<TournamentArrow> getArrows() {
        return arrows;
    }

    public void setArrows(List<TournamentArrow> arrows) {
        this.arrows = arrows;
    }
}
