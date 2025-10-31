package com.loopers.domain.members;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberModel registerMember(String memberId, String name, String email, String password, String birthDate, String gender) {
        if (memberRepository.existsByMemberId(memberId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 사용자 ID입니다.");
        }
        
        String encodedPassword = passwordEncoder.encode(password);
        MemberModel member = MemberModel.create(memberId, name, email, encodedPassword, birthDate, gender);
        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public MemberModel getMember(String memberId) {
        return memberRepository.findByMemberId(memberId).orElse(null);
    }
}
