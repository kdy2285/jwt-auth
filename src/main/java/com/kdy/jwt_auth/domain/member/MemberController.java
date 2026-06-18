package com.kdy.jwt_auth.domain.member;

import com.kdy.jwt_auth.common.response.ApiResponse;
import com.kdy.jwt_auth.security.jwt.JwtPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemberController {

    @GetMapping("/api/members/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.success(
                new MeResponse(
                        principal.memberId(),
                        principal.email(),
                        principal.role().name(
                        )
                )
        );
    }

    public record MeResponse(
            Long memberId,
            String email,
            String role
    ) {
    }
}
