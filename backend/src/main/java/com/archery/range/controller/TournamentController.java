package com.archery.range.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.archery.range.common.ApiResponse;
import com.archery.range.dto.TournamentDtos;
import com.archery.range.service.TournamentService;

import jakarta.validation.Valid;

/**
 * 馆内团体淘汰赛计分台。
 *
 * 三组分页：
 *   赛事总览  GET /tournaments、/tournaments/{id}
 *   对阵树    GET /tournaments/{id}/bracket（晋级关系与胜者全部来自服务端）
 *   单场计分区 GET /tournaments/matches/{id} + 局 / 加赛记分动作
 *
 * 写操作的角色由请求体 role 显式带入（MANAGER / REFEREE / MEMBER），服务端逐项收口，越权返回「无权操作」。
 */
@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {

    private final TournamentService tournamentService;

    public TournamentController(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    // ---------------- 基础数据（只读，所有角色可看） ----------------

    @GetMapping("/members/options")
    public ApiResponse<List<TournamentDtos.MemberOption>> options() {
        return ApiResponse.success(tournamentService.memberOptions());
    }

    // ---------------- 赛事总览 ----------------

    @GetMapping
    public ApiResponse<List<TournamentDtos.TournamentSummary>> list() {
        return ApiResponse.success(tournamentService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<TournamentDtos.TournamentView> detail(@PathVariable Long id) {
        return ApiResponse.success(tournamentService.detail(id));
    }

    @GetMapping("/{id}/bracket")
    public ApiResponse<TournamentDtos.BracketView> bracket(@PathVariable Long id) {
        return ApiResponse.success(tournamentService.bracket(id));
    }

    // ---------------- 值班经理：赛事与参赛队 ----------------

    @PostMapping
    public ApiResponse<TournamentDtos.TournamentView> create(
            @Valid @RequestBody TournamentDtos.CreateTournamentReq req) {
        return ApiResponse.success("赛事已建立", tournamentService.create(req));
    }

    @PostMapping("/{id}/teams")
    public ApiResponse<TournamentDtos.TournamentView> upsertTeam(
            @PathVariable Long id, @Valid @RequestBody TournamentDtos.TeamUpsertReq req) {
        return ApiResponse.success("队伍已保存", tournamentService.upsertTeam(id, req));
    }

    @DeleteMapping("/{id}/teams/{slotNo}")
    public ApiResponse<TournamentDtos.TournamentView> removeTeam(
            @PathVariable Long id, @PathVariable Integer slotNo,
            @Valid @RequestBody TournamentDtos.OperatorRoleReq req) {
        return ApiResponse.success("队伍已删除", tournamentService.removeTeam(id, slotNo, req));
    }

    @PostMapping("/{id}/start")
    public ApiResponse<TournamentDtos.TournamentView> start(
            @PathVariable Long id, @Valid @RequestBody TournamentDtos.OperatorRoleReq req) {
        return ApiResponse.success("赛事已开始", tournamentService.start(id, req));
    }

    // ---------------- 裁判：单场计分 ----------------

    @GetMapping("/matches/{matchId}")
    public ApiResponse<TournamentDtos.MatchView> matchDetail(@PathVariable Long matchId) {
        return ApiResponse.success(tournamentService.matchDetail(matchId));
    }

    @PostMapping("/matches/{matchId}/start")
    public ApiResponse<TournamentDtos.MatchView> startMatch(
            @PathVariable Long matchId, @Valid @RequestBody TournamentDtos.OperatorRoleReq req) {
        return ApiResponse.success("场次已开赛", tournamentService.startMatch(matchId, req));
    }

    @PostMapping("/matches/{matchId}/games")
    public ApiResponse<TournamentDtos.MatchView> createGame(
            @PathVariable Long matchId, @Valid @RequestBody TournamentDtos.OperatorRoleReq req) {
        return ApiResponse.success("已进入新一局", tournamentService.createGame(matchId, req));
    }

    @PostMapping("/matches/{matchId}/withdraw-last")
    public ApiResponse<TournamentDtos.MatchView> withdrawLast(
            @PathVariable Long matchId, @Valid @RequestBody TournamentDtos.WithdrawReq req) {
        return ApiResponse.success("已撤回最后一局", tournamentService.withdrawLastGame(matchId, req));
    }

    @PostMapping("/matches/{matchId}/confirm-winner")
    public ApiResponse<TournamentDtos.MatchView> confirmWinner(
            @PathVariable Long matchId, @Valid @RequestBody TournamentDtos.OperatorRoleReq req) {
        return ApiResponse.success("胜者已确认", tournamentService.confirmWinner(matchId, req));
    }

    @PostMapping("/matches/{matchId}/playoffs")
    public ApiResponse<TournamentDtos.MatchView> startPlayoff(
            @PathVariable Long matchId, @Valid @RequestBody TournamentDtos.OperatorRoleReq req) {
        return ApiResponse.success("已进入加赛", tournamentService.startPlayoff(matchId, req));
    }

    // ---------------- 局内 / 加赛箭 ----------------

    @PostMapping("/games/{gameId}/arrows")
    public ApiResponse<TournamentDtos.MatchView> shootGame(
            @PathVariable Long gameId, @Valid @RequestBody TournamentDtos.ArrowReq req) {
        return ApiResponse.success("已记一支箭", tournamentService.shootGame(gameId, req));
    }

    @PostMapping("/games/{gameId}/confirm")
    public ApiResponse<TournamentDtos.MatchView> confirmGame(
            @PathVariable Long gameId, @Valid @RequestBody TournamentDtos.OperatorRoleReq req) {
        return ApiResponse.success("本局已确认", tournamentService.confirmGame(gameId, req));
    }

    @PostMapping("/playoffs/{playoffRoundId}/arrows")
    public ApiResponse<TournamentDtos.MatchView> shootPlayoff(
            @PathVariable Long playoffRoundId, @Valid @RequestBody TournamentDtos.ArrowReq req) {
        return ApiResponse.success("已记一支加赛箭", tournamentService.shootPlayoff(playoffRoundId, req));
    }

    @PostMapping("/playoffs/{playoffRoundId}/confirm")
    public ApiResponse<TournamentDtos.MatchView> confirmPlayoff(
            @PathVariable Long playoffRoundId, @Valid @RequestBody TournamentDtos.OperatorRoleReq req) {
        return ApiResponse.success("本轮加赛已确认", tournamentService.confirmPlayoff(playoffRoundId, req));
    }
}
