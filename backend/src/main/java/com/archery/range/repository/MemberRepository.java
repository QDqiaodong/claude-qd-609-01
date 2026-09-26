package com.archery.range.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.archery.range.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findAllByOrderByIdAsc();

    Optional<Member> findByCardNo(String cardNo);

    List<Member> findByLevelOrderByIdAsc(String level);

    boolean existsByPhone(String phone);

    boolean existsByCardNo(String cardNo);
}
