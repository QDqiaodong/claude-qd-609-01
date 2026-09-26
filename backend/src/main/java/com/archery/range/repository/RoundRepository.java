package com.archery.range.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.archery.range.domain.Round;

import jakarta.persistence.LockModeType;

public interface RoundRepository extends JpaRepository<Round, Long> {

    List<Round> findAllByOrderByStartTimeDesc();

    List<Round> findByMemberIdOrderByStartTimeDesc(Long memberId);

    List<Round> findByLaneIdOrderByStartTimeDesc(Long laneId);

    List<Round> findByStatusOrderByStartTimeDesc(String status);

    List<Round> findByMemberIdAndStatusOrderByStartTimeDesc(Long memberId, String status);

    boolean existsByRoundNo(String roundNo);

    long countByMemberId(Long memberId);

    long countByStatus(String status);

    /** 悲观写锁：记箭 / 撤销 / 提交与安全联锁的暂停、恢复互斥 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Round r where r.id = :id")
    Optional<Round> findByIdForUpdate(@Param("id") Long id);

    /** 悲观写锁：停射事件批量暂停受影响箭道上进行中的回合 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Round r where r.lane.id in :laneIds and r.status = 'ONGOING' order by r.id")
    List<Round> findOngoingByLaneIdsForUpdate(@Param("laneIds") List<Long> laneIds);
}
