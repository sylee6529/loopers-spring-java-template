package com.loopers.domain.members;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberModelTest {

    @DisplayName("Member 객체를 생성 시,")
    @Nested
    class CreateMemberTest {

        @DisplayName("정상 값이 들어오면 User 객체 생성에 성공한다")
        @Test
        void shouldSaveMember_whenRequestIsValid() {
            MemberModel member = MemberModel.create("test123", "홍길동", "test@example.com", "password123", "1990-01-01", "MALE");

            assertThat(member).isNotNull();
            assertThat(member.getMemberId()).isEqualTo("test123");
            assertThat(member.getEmail()).isEqualTo("test@example.com");
            assertThat(member.getBirthDate()).isEqualTo(java.time.LocalDate.of(1990, 1, 1));
            assertThat(member.getGender()).isEqualTo("MALE");

        }

        @ParameterizedTest
        @ValueSource(strings = {"abcdefghijklmn", "abc123daas456"})
        @DisplayName("Member ID가 10자 초과이면 예외가 발생한다")
        void shouldThrowException_whenMemberIdTooLong(String invalidMemberId) {
            assertThatThrownBy(() ->
                    MemberModel.create(
                            invalidMemberId, "홍길동", "test@example.com", "password", "2002-02-02", "FEMALE"
                    )
            ).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자 ID는 영문 및 숫자 10자 이내여야 합니다.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"aaa", "bcdef"})
        @DisplayName("ID가 영문으로만 이루어지면 예외가 발생한다")
        void shouldThrowException_IfMemberIdIsLetterOnly(String letterOnlyId) {
            assertThatThrownBy(() ->
                    MemberModel.create(letterOnlyId, "홍길동", "test@example.com", "password", "2002-02-02", "FEMALE")
            ).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자 ID는 영문 및 숫자 10자 이내여야 합니다.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"123", "44445"})
        @DisplayName("ID가 숫자로만 이루어지면 예외가 발생한다")
        void shouldThrowException_IfMemberIdIsNumberOnly(String numberOnlyId) {
            assertThatThrownBy(() ->
                    MemberModel.create(numberOnlyId, "홍길동", "test@example.com", "password", "2002-02-02", "FEMALE")
            ).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자 ID는 영문 및 숫자 10자 이내여야 합니다.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"test@", "@example.com", "test@example"})
        @DisplayName("이메일이 xx@yy.zz 형식에 맞지 않으면, User 객체 생성에 실패한다")
        void shouldThrowException_whenEmailFormatIsInvalid(String invalidEmail) {
            assertThatThrownBy(() ->
                    MemberModel.create("test123", "홍길동", invalidEmail, "password", "1990-01-01", "MALE")
            ).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이메일 형식이 올바르지 않습니다.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"19900101", "1990/01/01", "90-01-01", "1990-13-01", "1990-01-32"})
        @DisplayName("생년월일이 yyyy-MM-dd 형식에 맞지 않으면, User 객체 생성에 실패한다")
        void shouldThrowException_whenBirthDateFormatIsInvalid(String invalidBirthDate) {
            assertThatThrownBy(() ->
                    MemberModel.create("test123", "홍길동", "test@example.com", "password", invalidBirthDate, "MALE")
            ).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("생년월일 형식이 올바르지 않습니다.");
        }

    }
}
