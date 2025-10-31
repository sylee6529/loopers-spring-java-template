package com.loopers.infrastructure.members;

import com.loopers.domain.members.MemberModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberJpaRepository extends JpaRepository<MemberModel, Long> {
    Optional<MemberModel> findByMemberId(String memberId);
    boolean existsByMemberId(String memberId);
}