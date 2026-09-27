package com.archery.range.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 馆内团体淘汰赛计分台的入参与出参。
 *
 * 所有写操作都带 operator（姓名）与 role（MANAGER 值班经理 / REFEREE 裁判 / MEMBER 普通会员），
 * 服务端按角色收口：经理管赛事与参赛队、裁判推进比赛与记分、普通会员只读。
 */
public final class TournamentDtos {

    private TournamentDtos() {
    }

    // ---------------- 入参 ----------------

    /** 值班经理建立赛事：4 或 8 支队、每场局数、每局每名队员规定箭数 */
    public record CreateTournamentReq(
            @NotBlank(message = "请填写赛事名称") @Size(max = 64, message = "赛事名称不能超过 64 字") String name,
            @NotNull(message = "请选择参赛队数（4 或 8）") Integer teamSize,
            @NotNull(message = "请设置每场局数") @Min(value = 1, message = "每场局数至少 1 局") @Max(value = 12, message = "每场局数不能超过 12 局") Integer gamesPerMatch,
            @NotNull(message = "请设置每局每名队员的规定箭数") @Min(value = 1, message = "每人每局至少射 1 支") @Max(value = 6, message = "每人每局不能超过 6 支") Integer arrowsPerMember,
            @NotBlank(message = "请填写值班经理姓名") String operator,
            @NotBlank(message = "缺少角色信息") String role) {
    }

    /** 筹备期建队 / 换队：一个签位 + 队名 + 三名现有会员 */
    public record TeamUpsertReq(
            @NotNull(message = "请选择队的签位") Integer slotNo,
            @NotBlank(message = "请填写队名") @Size(max = 48, message = "队名不能超过 48 字") String teamName,
            @NotNull(message = "请选择三名队员") @Size(min = 3, max = 3, message = "每队必须且只能是三名队员") List<@NotNull Long> memberIds,
            @NotBlank(message = "请填写值班经理姓名") String operator,
            @NotBlank(message = "缺少角色信息") String role) {
    }

    /** 只带操作人 + 角色的动作：开赛 / 开赛场次 / 新建局 / 确认局 / 确认胜者 / 开加赛轮 / 确认加赛轮 */
    public record OperatorRoleReq(
            @NotBlank(message = "请填写操作人姓名") String operator,
            @NotBlank(message = "缺少角色信息") String role) {
    }

    /** 记一支箭（局内或加赛轮内） */
    public record ArrowReq(
            @NotNull(message = "缺少队员信息") Long memberId,
            @NotBlank(message = "请选择环数") String ring,
            @NotBlank(message = "请填写裁判姓名") String operator,
            @NotBlank(message = "缺少角色信息") String role) {
    }

    /** 撤回当前未结束比赛的最后一局：必须填写原因 */
    public record WithdrawReq(
            @NotBlank(message = "请填写裁判姓名") String operator,
            @NotBlank(message = "缺少角色信息") String role,
            @NotBlank(message = "撤回最后一局必须填写原因") @Size(max = 200, message = "撤回原因不能超过 200 字") String reason) {
    }

    // ---------------- 出参 ----------------

    public record MemberOption(Long id, String name, String cardNo) {
    }

    public record TeamMemberView(Long memberId, String name, String cardNo, Integer seatNo) {
    }

    public record TeamView(
            Long id,
            Integer slotNo,
            String teamName,
            List<TeamMemberView> members) {
    }

    public record ArrowView(
            Integer shotIndex,
            Long memberId,
            String memberName,
            Long teamId,
            String teamName,
            String side,
            String ring,
            Integer ringValue) {
    }

    public record GameView(
            Long id,
            Integer gameNo,
            String status,
            String statusName,
            Integer scoreA,
            Integer scoreB,
            Integer xA,
            Integer xB,
            String createdBy,
            String confirmedBy,
            LocalDateTime createdAt,
            LocalDateTime confirmedAt,
            List<ArrowView> arrows) {
    }

    public record PlayoffRoundView(
            Long id,
            Integer roundNo,
            String status,
            String statusName,
            Integer scoreA,
            Integer scoreB,
            Integer xA,
            Integer xB,
            Long winnerTeamId,
            String winnerTeamName,
            String createdBy,
            String confirmedBy,
            LocalDateTime createdAt,
            LocalDateTime decidedAt,
            List<ArrowView> arrows) {
    }

    /** 某队在本场比赛中的信息 */
    public record MatchSide(
            Long teamId,
            String teamName,
            Integer seed,
            List<TeamMemberView> members) {
    }

    /** 晋级来源：来源场次槽位、来源场次状态与已产生的胜者 */
    public record SourceInfo(
            Integer slotNo,
            String matchStatus,
            String matchStatusName,
            Long winnerTeamId,
            String winnerTeamName) {
    }

    public record MatchView(
            Long id,
            Integer slotNo,
            Integer roundNo,
            String status,
            String statusName,
            MatchSide teamA,
            MatchSide teamB,
            SourceInfo sourceA,
            SourceInfo sourceB,
            Long winnerTeamId,
            String winnerTeamName,
            String winMode,
            String winModeName,
            Integer regScoreA,
            Integer regScoreB,
            Integer regXA,
            Integer regXB,
            Integer gamesPerMatch,
            Integer arrowsPerMember,
            Integer gameCount,
            Integer playoffRoundCount,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            List<GameView> games,
            List<PlayoffRoundView> playoffs) {
    }

    public record LogView(
            Long id,
            Long matchId,
            Integer matchSlotNo,
            String action,
            String actionName,
            String operator,
            String role,
            String roleName,
            String detail,
            LocalDateTime createdAt) {
    }

    public record TournamentSummary(
            Long id,
            String tournamentNo,
            String name,
            String status,
            String statusName,
            Integer teamSize,
            Integer gamesPerMatch,
            Integer arrowsPerMember,
            Integer currentRound,
            Integer totalRounds,
            Long championTeamId,
            String championTeamName,
            Integer teamCount,
            Integer pendingCount,
            Integer liveCount,
            Integer finishedCount,
            Integer playoffMatchCount,
            LocalDateTime createdAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt) {
    }

    public record TournamentView(
            Long id,
            String tournamentNo,
            String name,
            String status,
            String statusName,
            Integer teamSize,
            Integer gamesPerMatch,
            Integer arrowsPerMember,
            Integer currentRound,
            Integer totalRounds,
            Long championTeamId,
            String championTeamName,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            List<TeamView> teams,
            List<MatchView> matches,
            List<LogView> logs) {
    }

    /** 对阵树：按轮分组，关系与胜者全部由服务端给出，前端只负责渲染 */
    public record BracketView(
            Long tournamentId,
            String tournamentName,
            String status,
            Integer currentRound,
            Integer totalRounds,
            List<BracketRound> rounds) {
    }

    public record BracketRound(Integer roundNo, List<MatchView> matches) {
    }
}
