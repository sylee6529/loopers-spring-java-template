package com.loopers.application.members;

import com.loopers.domain.members.MemberModel;

import java.time.LocalDate;

public record MemberInfo(Long id, String memberId, String name, String email, LocalDate birthDate, String gender) {
    public static MemberInfo from(MemberModel model) {
        return new MemberInfo(
                model.getId(),
                model.getMemberId(),
                model.getName(),
                model.getEmail(),
                model.getBirthDate(),
                model.getGender()
        );
    }
}
