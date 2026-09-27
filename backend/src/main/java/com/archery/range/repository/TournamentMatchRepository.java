package com.archery.range.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.archery.range.domain.TournamentMatch;

import jakarta.persistence.LockModeType;

public interface TournamentMatchRepository extends JpaRepository<TournamentMatch, Long> {

    List<TournamentMatch> findByTournamentIdOrderBySlotNoAsc(Long tournamentId);

    List<TournamentMatch> findByTournamentIdAndRoundNoOrderBySlotNoAsc(Long tournamentId, Integer roundNo);

    long countByTournamentIdAndRoundNoAndStatus(Long tournamentId, Integer roundNo, String status);

    Optional<TournamentMatch> findByTournamentIdAndSlotNo(Long tournamentId, Integer slotNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from TournamentMatch m where m.id = :id")
    Optional<TournamentMatch> findByIdForUpdate(@Param("id") Long id);
}
