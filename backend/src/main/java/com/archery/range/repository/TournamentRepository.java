package com.archery.range.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.archery.range.domain.Tournament;

import jakarta.persistence.LockModeType;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    boolean existsByTournamentNo(String tournamentNo);

    List<Tournament> findAllByOrderByIdDesc();

    long countByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tournament t where t.id = :id")
    Optional<Tournament> findByIdForUpdate(@Param("id") Long id);
}
