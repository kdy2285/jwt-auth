package com.kdy.jwt_auth.auth;

import com.kdy.jwt_auth.auth.dto.LoginRequest;
import com.kdy.jwt_auth.auth.dto.RefreshTokenRequest;
import com.kdy.jwt_auth.auth.dto.SignupRequest;
import com.kdy.jwt_auth.auth.dto.TokenResponse;
import com.kdy.jwt_auth.common.exception.BusinessException;
import com.kdy.jwt_auth.common.exception.ErrorCode;
import com.kdy.jwt_auth.domain.member.Member;
import com.kdy.jwt_auth.domain.member.MemberRepository;
import com.kdy.jwt_auth.domain.member.Role;
import com.kdy.jwt_auth.security.jwt.JwtPrincipal;
import com.kdy.jwt_auth.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public void signup(SignupRequest request) {
        if(memberRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATED_EMAIL);
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        Member member = new Member(
                request.getEmail(),
                encodedPassword,
                Role.USER
        );

        memberRepository.save(member);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LOGIN));

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }

        String accessToken = jwtTokenProvider.createAccessToken(member);
        String refreshToken = jwtTokenProvider.createRefreshToken(member);

        member.updateRefreshToken(refreshToken);

        return TokenResponse.bearer(accessToken, refreshToken);
    }

    @Transactional
    public TokenResponse reissue(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if(!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        JwtPrincipal principal = jwtTokenProvider.getPrincipal(refreshToken);

        Member member = memberRepository.findById(principal.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (member.getRefreshToken() == null || !member.getRefreshToken().equals(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(member);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(member);

        member.updateRefreshToken(newRefreshToken);

        return TokenResponse.bearer(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        member.clearRefreshToken();
    }
}
