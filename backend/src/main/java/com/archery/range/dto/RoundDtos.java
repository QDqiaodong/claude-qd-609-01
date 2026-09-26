package com.archery.range.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 计分回合模块的入参与出参。
 *
 * ArrowView 的 shotIndex 就是有序集合里的位置（从 1 开始），
 * 前端按它顺序渲染箭支徽章。
 */
public final class RoundDtos {

    private RoundDtos() {
    }

    public record RoundStartReq(
            @NotNull(message = "请选择会员") Long memberId,
            @NotNull(message = "请选择箭道") Long laneId,
            @NotNull(message = "请选择箭支数") Integer arrowCount) {
    }

    /** 记一支箭：环数标记 X / 10 / 9 ... / M */
    public record ShotReq(@NotBlank(message = "请选择环数") String ring) {
    }

    public record ArrowView(Integer shotIndex, String ring, Integer ringValue) {
    }

    public record RoundView(
            Long id,
            String roundNo,
            Long memberId,
            String memberName,
            String memberCardNo,
            Long laneId,
            String laneNo,
            Integer distance,
            LocalDateTime startTime,
            Integer arrowCount,
            Integer shotCount,
            Integer totalScore,
            BigDecimal averageScore,
            Boolean personalBest,
            String status,
            String statusName) {
    }

    /** 成绩单：回合 + 按 shot_index 顺序排好的每一支箭 */
    public record RoundDetailView(RoundView round, List<ArrowView> arrows) {
    }
}
