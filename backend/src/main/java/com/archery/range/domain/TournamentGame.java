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
 * 比赛中的一局。
 *
 * 一局只有两队各三名队员的规定箭数全部录完才能确认（CONFIRMED）；
 * 确认后的局只读，改错只能撤回「当前未结束比赛的最后一局」。
 * arrows 是有序集合，shot_index 即箭序（全局 0..6n-1）。
 */
@Entity
@Table(name = "tournament_game")
public class TournamentGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "game_no", nullable = false)
    private Integer gameNo;

    /** ENTERING 录入中 / CONFIRMED 已确认 */
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

    @Column(name = "created_by", length = 32)
    private String createdBy;

    @Column(name = "confirmed_by", length = 32)
    private String confirmedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /*
     * 有序集合映射（与日常计分 round_arrow 同款）：
     * @OneToMany + @JoinTable + @OrderColumn，Hibernate 在 tournament_game_arrow 维护 shot_index，
     * 列表下标即箭序；append 后 save 即可，撤回整局时级联删除连接行与箭。
     */
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(
            name = "tournament_game_arrow",
            joinColumns = @JoinColumn(name = "game_id"),
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

    public Integer getGameNo() {
        return gameNo;
    }

    public void setGameNo(Integer gameNo) {
        this.gameNo = gameNo;
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

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public List<TournamentArrow> getArrows() {
        return arrows;
    }

    public void setArrows(List<TournamentArrow> arrows) {
        this.arrows = arrows;
    }
}
