package com.archery.range.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.archery.range.domain.TournamentGame;

public interface TournamentGameRepository extends JpaRepository<TournamentGame, Long> {

    List<TournamentGame> findByMatchIdOrderByGameNoAsc(Long matchId);
}
