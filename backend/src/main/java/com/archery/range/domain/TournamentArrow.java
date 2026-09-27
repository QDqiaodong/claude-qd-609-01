package com.archery.range.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 淘汰赛计分箭：环数标记（X / 10 / 9 ... / 1 / M）与计分环值，口径与日常计分完全一致。
 * 独立于 arrow_score，避免影响日常计分模块的演示数据。
 *
 * member_id / team_id 随箭落库，标识「这支箭是谁、属于哪支队」；
 * 箭与局 / 加赛轮的有序关系由 TournamentGame / TournamentPlayoffRound 的
 * @JoinTable + @OrderColumn 维护（与日常 round_arrow 同一套有序集合映射）。
 */
@Entity
@Table(name = "tournament_arrow")
public class TournamentArrow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 4)
    private String ring;

    @Column(name = "ring_value", nullable = false)
    private Integer ringValue;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "team_id")
    private Long teamId;

    public TournamentArrow() {
    }

    public TournamentArrow(String ring, Integer ringValue) {
        this.ring = ring;
        this.ringValue = ringValue;
    }

    public TournamentArrow(String ring, Integer ringValue, Long memberId, Long teamId) {
        this.ring = ring;
        this.ringValue = ringValue;
        this.memberId = memberId;
        this.teamId = teamId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRing() {
        return ring;
    }

    public void setRing(String ring) {
        this.ring = ring;
    }

    public Integer getRingValue() {
        return ringValue;
    }

    public void setRingValue(Integer ringValue) {
        this.ringValue = ringValue;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }
}
