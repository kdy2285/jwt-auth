package com.kdy.jwt_auth.security.jwt;

import com.kdy.jwt_auth.domain.member.Member;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import com.kdy.jwt_auth.domain.member.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

@Component
public class JwtTokenProvider {

    public static final String ACCESS_TYPE = "ACCESS";
    public static final String REFRESH_TYPE = "REFRESH";
    private static final String TYPE_CLAIM = "type";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Member member) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(member.getId()))
                .claim("memberId", member.getId())
                .claim("email", member.getEmail())
                .claim("role", member.getRole().name())
                .claim(TYPE_CLAIM, ACCESS_TYPE)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * 서명과 만료가 유효하고, type claim이 기대한 값인지 함께 검사한다.
     * 같은 키로 서명한 Refresh Token이 Access Token 자리에 쓰이는 것을 막기 위함이다.
     */
    public boolean validateToken(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            return expectedType.equals(claims.get(TYPE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public JwtPrincipal getPrincipal(String token) {
        Claims claims = parseClaims(token);

        Long memberId = claims.get("memberId", Long.class);
        String email = claims.get("email", String.class);
        Role role = Role.valueOf(claims.get("role", String.class));

        return new JwtPrincipal(memberId, email, role);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String createRefreshToken(Member member) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(member.getId()))
                .claim("memberId", member.getId())
                .claim("email", member.getEmail())
                .claim("role", member.getRole().name())
                .claim(TYPE_CLAIM, REFRESH_TYPE)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }
}
