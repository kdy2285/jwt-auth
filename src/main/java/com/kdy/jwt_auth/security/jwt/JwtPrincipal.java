package com.kdy.jwt_auth.security.jwt;

import com.kdy.jwt_auth.domain.member.Role;

public record JwtPrincipal(
        Long memberId,
        String email,
        Role role
) {
}
