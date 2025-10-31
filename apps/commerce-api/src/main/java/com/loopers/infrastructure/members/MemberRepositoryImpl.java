package com.loopers.infrastructure.members;

import com.loopers.domain.members.MemberModel;
import com.loopers.domain.members.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class MemberRepositoryImpl implements MemberRepository {
    private final MemberJpaRepository memberJpaRepository;

    @Override
    public Optional<MemberModel> findByMemberId(String memberId) {
        return memberJpaRepository.findByMemberId(memberId);
    }

    @Override
    public MemberModel save(MemberModel memberModel) {
        return memberJpaRepository.save(memberModel);
    }

    @Override
    public boolean existsByMemberId(String memberId) {
        return memberJpaRepository.existsByMemberId(memberId);
    }
}
