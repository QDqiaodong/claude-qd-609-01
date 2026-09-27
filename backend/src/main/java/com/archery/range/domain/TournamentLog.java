package com.archery.range.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 赛事完整操作轨迹：建队 / 换队 / 开赛 / 开赛场次 / 记局 / 确认局 /
 * 撤回最后一局（撤回人、时间、原因）/ 加赛 / 确认胜者 / 自动晋级 / 夺冠，全程留痕。
 */
@Entity
@Table(name = "tournament_log")
public class TournamentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @Column(name = "match_id")
    private Long matchId;

    /** CREATE_TEAM / UPDATE_TEAM / REMOVE_TEAM / START_TOURNAMENT / START_MATCH /
     *  SAVE_GAME / CONFIRM_GAME / WITHDRAW_GAME / START_PLAYOFF / CONFIRM_PLAYOFF /
     *  CONFIRM_WINNER / ADVANCE / CHAMPION */
    @Column(nullable = false, length = 24)
    private String action;

    @Column(nullable = false, length = 32)
    private String operator;

    /** MANAGER 值班经理 / REFEREE 裁判 / SYSTEM 系统（自动晋级） */
    @Column(nullable = false, length = 16)
    private String role;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

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

    public Long getMatchId() {
        return matchId;
    }

    public void setMatchId(Long matchId) {
        this.matchId = matchId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
