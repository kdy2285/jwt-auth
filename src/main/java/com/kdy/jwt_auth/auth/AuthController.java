package com.kdy.jwt_auth.auth;

import com.kdy.jwt_auth.auth.dto.LoginRequest;
import com.kdy.jwt_auth.auth.dto.RefreshTokenRequest;
import com.kdy.jwt_auth.auth.dto.SignupRequest;
import com.kdy.jwt_auth.auth.dto.TokenResponse;
import com.kdy.jwt_auth.common.response.ApiResponse;
import com.kdy.jwt_auth.security.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ApiResponse.success();
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse response = authService.reissue(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal JwtPrincipal principal) {
        authService.logout(principal.memberId());
        return ApiResponse.success();
    }
}
