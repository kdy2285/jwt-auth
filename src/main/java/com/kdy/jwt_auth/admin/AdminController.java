package com.kdy.jwt_auth.admin;

import com.kdy.jwt_auth.common.response.ApiResponse;
import com.kdy.jwt_auth.security.jwt.JwtPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

    @GetMapping("/api/admin/test")
    public ApiResponse<AdminTestResponse> adminTest(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ApiResponse.success(
                new AdminTestResponse(
                        principal.memberId(),
                        principal.email(),
                        principal.role().name(),
                        "ADMIN API 접근 성공"
                )
        );
    }

    public record AdminTestResponse(
            Long memberId,
            String email,
            String role,
            String message
    ) {
    }
}
