package com.loopers.application.members;

import com.loopers.domain.members.MemberModel;
import com.loopers.domain.members.MemberService;
import com.loopers.domain.points.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Component
public class MemberFacade {
    private final MemberService memberService;
    private final PointService pointService;

    public MemberInfo registerMember(String memberId, String name, String email, String password, String birthDate, String gender) {
        MemberModel member = memberService.registerMember(memberId, name, email, password, birthDate, gender);
        pointService.initializeMemberPoints(memberId);
        return MemberInfo.from(member);
    }

    public MemberInfo getMemberInfo(String memberId) {
        MemberModel member = memberService.getMember(memberId);
        return member != null ? MemberInfo.from(member) : null;
    }

    public BigDecimal getMemberPoints(String memberId) {
        return pointService.getMemberPoints(memberId);
    }
}
