package com.loopers.domain.members;

import java.util.Optional;

public interface MemberRepository {
    Optional<MemberModel> findByMemberId(String memberId);

    MemberModel save(MemberModel memberModel);

    boolean existsByMemberId(String memberId);
}
