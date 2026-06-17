package com.kdy.jwt_auth.auth;

import com.kdy.jwt_auth.auth.dto.SignupRequest;
import com.kdy.jwt_auth.common.exception.BusinessException;
import com.kdy.jwt_auth.common.exception.ErrorCode;
import com.kdy.jwt_auth.domain.member.Member;
import com.kdy.jwt_auth.domain.member.MemberRepository;
import com.kdy.jwt_auth.domain.member.Role;
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
}
