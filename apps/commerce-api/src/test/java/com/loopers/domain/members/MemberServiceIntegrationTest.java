package com.loopers.domain.members;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class MemberServiceIntegrationTest {

    @Autowired
    private MemberService memberService;

    @MockitoSpyBean
    private MemberRepository memberRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원 가입 시,")
    @Nested
    class RegisterMember {

        @DisplayName("회원 가입시 User 저장이 수행된다 (spy)")
        @Test
        void shouldSaveMember_whenMemberRegisters() {
            assertThat(memberRepository).isNotNull();
            memberService.registerMember("test123", "홍길동", "test@example.com", "password", "1990-01-01", "MALE");
            verify(memberRepository, times(1)).save(any(MemberModel.class));

            boolean exists = memberRepository.existsByMemberId("test123");
            assertThat(exists).isTrue();
        }

        @DisplayName("이미 가입된 ID로 회원가입 시도 시, 실패한다")
        @Test
        void shouldThrowException_whenDuplicateMemberIdIsUsed() {
            memberService.registerMember("test123", "홍길동", "test@example.com", "password", "1990-01-01", "MALE");

            CoreException exception = assertThrows(CoreException.class, () -> {
                memberService.registerMember("test123", "김철수", "kim@example.com", "password", "1985-05-05", "MALE");
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("회원 조회 시,")
    @Nested
    class GetMember {

        @DisplayName("해당 ID의 회원이 존재할 경우, 회원 정보가 반환된다")
        @Test
        void shouldReturnMemberInfo_whenMemberExists() {
            memberService.registerMember("test123", "홍길동", "test@example.com", "password", "1990-01-01", "MALE");

            MemberModel result = memberService.getMember("test123");

            assertAll(
                    () -> assertThat(result).isNotNull(),
                    () -> assertThat(result.getMemberId()).isEqualTo("test123"),
                    () -> assertThat(result.getName()).isEqualTo("홍길동"),
                    () -> assertThat(result.getEmail()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("해당 ID의 회원이 존재하지 않을 경우, null이 반환된다")
        @Test
        void shouldReturnNull_whenMemberDoesNotExist() {
            MemberModel result = memberService.getMember("nonexistent");

            assertThat(result).isNull();
        }
    }
}
