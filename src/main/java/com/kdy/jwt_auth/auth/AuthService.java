package com.kdy.jwt_auth.auth;

import com.kdy.jwt_auth.auth.dto.LoginRequest;
import com.kdy.jwt_auth.auth.dto.SignupRequest;
import com.kdy.jwt_auth.auth.dto.TokenResponse;
import com.kdy.jwt_auth.common.exception.BusinessException;
import com.kdy.jwt_auth.common.exception.ErrorCode;
import com.kdy.jwt_auth.domain.member.Member;
import com.kdy.jwt_auth.domain.member.MemberRepository;
import com.kdy.jwt_auth.domain.member.Role;
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

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LOGIN));

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }

        String accessToken = jwtTokenProvider.createAccessToken(member);

        return TokenResponse.bearer(accessToken);
    }
}
