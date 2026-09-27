package com.archery.range.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.archery.range.domain.TournamentTeamMember;

public interface TournamentTeamMemberRepository extends JpaRepository<TournamentTeamMember, Long> {

    List<TournamentTeamMember> findByTournamentIdOrderByTeamIdAscSeatNoAsc(Long tournamentId);

    List<TournamentTeamMember> findByTeamIdOrderBySeatNoAsc(Long teamId);

    boolean existsByTournamentIdAndMemberId(Long tournamentId, Long memberId);
}
