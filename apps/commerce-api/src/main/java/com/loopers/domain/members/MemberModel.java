package com.loopers.domain.members;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {

    private static final Pattern MEMBER_ID_PATTERN = Pattern.compile("^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9]{1,10}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@]+@[^@]+\\.[^@]+$");
    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Column(unique = true, nullable = false, length = 10)
    private String memberId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private String gender;

    public static MemberModel create(String memberId, String name, String email, String password, String birthDate, String gender) {
        validateMemberId(memberId);
        validateEmail(email);
        LocalDate parsedBirthDate = validateBirthDate(birthDate);
        validateGender(gender);

        return new MemberModel(
                memberId,
                name,
                email,
                password,
                parsedBirthDate,
                gender
        );
    }

    private static void validateMemberId(String MemberId) {
        if (MemberId == null || MemberId.isBlank()) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }

        if (!MEMBER_ID_PATTERN.matcher(MemberId).matches()) {
            throw new IllegalArgumentException("사용자 ID는 영문 및 숫자 10자 이내여야 합니다.");
        }

    }

    private static LocalDate validateBirthDate(String birthDate) {
        if (birthDate == null || birthDate.isBlank()) {
            throw new IllegalArgumentException("생년월일은 필수입니다.");
        }

        try {
            LocalDate parsedDate = LocalDate.parse(birthDate, BIRTH_DATE_FORMATTER);

            if (parsedDate.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("생년월일은 미래일 수 없습니다");
            }
            return parsedDate;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("생년월일 형식이 올바르지 않습니다.");
        }
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }
    }

    private static void validateGender(String gender) {
        if (gender == null || (!gender.equals("MALE") && !gender.equals("FEMALE"))) {
            throw new IllegalArgumentException("성별은 MALE 또는 FEMALE 이어야 합니다");
        }
    }
}
