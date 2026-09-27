package com.archery.range.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.archery.range.domain.TournamentPlayoffRound;

public interface TournamentPlayoffRoundRepository extends JpaRepository<TournamentPlayoffRound, Long> {

    List<TournamentPlayoffRound> findByMatchIdOrderByRoundNoAsc(Long matchId);

    Optional<TournamentPlayoffRound> findByMatchIdAndRoundNo(Long matchId, Integer roundNo);
}
