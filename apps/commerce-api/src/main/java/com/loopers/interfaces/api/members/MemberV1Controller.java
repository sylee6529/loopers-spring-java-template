package com.loopers.interfaces.api.members;

import com.loopers.application.members.MemberFacade;
import com.loopers.application.members.MemberInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberV1Controller implements MemberV1ApiSpec {

    private final MemberFacade memberFacade;

    @PostMapping("/register")
    @Override
    public ApiResponse<MemberV1Dto.MemberResponse> registerMember(
            @Valid @RequestBody MemberV1Dto.MemberRegisterRequest request
    ) {
        MemberInfo memberInfo = memberFacade.registerMember(
                request.memberId(),
                request.name(),
                request.email(),
                request.password(),
                request.birthDate(),
                request.gender()
        );
        
        MemberV1Dto.MemberResponse response = MemberV1Dto.MemberResponse.from(memberInfo);
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<MemberV1Dto.MemberResponse> getMemberInfo(
            @RequestHeader("X-USER-ID") String userId
    ) {
        MemberInfo memberInfo = memberFacade.getMemberInfo(userId);
        if (memberInfo == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
        
        MemberV1Dto.MemberResponse response = MemberV1Dto.MemberResponse.from(memberInfo);
        return ApiResponse.success(response);
    }

}