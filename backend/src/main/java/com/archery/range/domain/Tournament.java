package com.archery.range.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 团体淘汰赛赛事。
 *
 * 状态机：SETUP 筹备中（可建队/换队）→ ONGOING 已开赛（队伍与对阵冻结）→ FINISHED 已决出冠军。
 * 一旦进入 ONGOING，team_size / games_per_match / arrows_per_member 与队伍、对阵关系都不再可变。
 */
@Entity
@Table(name = "tournaments")
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_no", nullable = false, unique = true, length = 24)
    private String tournamentNo;

    @Column(nullable = false, length = 64)
    private String name;

    /** SETUP / ONGOING / FINISHED */
    @Column(nullable = false, length = 16)
    private String status;

    /** 参赛队数：4 或 8 */
    @Column(name = "team_size", nullable = false)
    private Integer teamSize;

    /** 每场局数 */
    @Column(name = "games_per_match", nullable = false)
    private Integer gamesPerMatch;

    /** 每局每名队员规定箭数 */
    @Column(name = "arrows_per_member", nullable = false)
    private Integer arrowsPerMember;

    /** 当前轮次（1 起） */
    @Column(name = "current_round", nullable = false)
    private Integer currentRound;

    /** 总轮次：4 队 = 2，8 队 = 3 */
    @Column(name = "total_rounds", nullable = false)
    private Integer totalRounds;

    @Column(name = "champion_team_id")
    private Long championTeamId;

    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

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

    public String getTournamentNo() {
        return tournamentNo;
    }

    public void setTournamentNo(String tournamentNo) {
        this.tournamentNo = tournamentNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTeamSize() {
        return teamSize;
    }

    public void setTeamSize(Integer teamSize) {
        this.teamSize = teamSize;
    }

    public Integer getGamesPerMatch() {
        return gamesPerMatch;
    }

    public void setGamesPerMatch(Integer gamesPerMatch) {
        this.gamesPerMatch = gamesPerMatch;
    }

    public Integer getArrowsPerMember() {
        return arrowsPerMember;
    }

    public void setArrowsPerMember(Integer arrowsPerMember) {
        this.arrowsPerMember = arrowsPerMember;
    }

    public Integer getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(Integer currentRound) {
        this.currentRound = currentRound;
    }

    public Integer getTotalRounds() {
        return totalRounds;
    }

    public void setTotalRounds(Integer totalRounds) {
        this.totalRounds = totalRounds;
    }

    public Long getChampionTeamId() {
        return championTeamId;
    }

    public void setChampionTeamId(Long championTeamId) {
        this.championTeamId = championTeamId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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
