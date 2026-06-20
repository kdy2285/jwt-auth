package com.kdy.jwt_auth.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdy.jwt_auth.domain.member.Member;
import com.kdy.jwt_auth.domain.member.MemberRepository;
import com.kdy.jwt_auth.domain.member.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("회원가입에 성공한다")
    void signup_success() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@test.com",
                                  "password": "12345678"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("중복 이메일로 회원가입하면 409를 반환한다")
    void signup_duplicateEmail_fail() throws Exception {
        signup("user@test.com", "12345678");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@test.com",
                                  "password": "12345678"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));
    }

    @Test
    @DisplayName("로그인에 성공하면 Access Token과 Refresh Token을 발급한다")
    void login_success() throws Exception {
        signup("user@test.com", "12345678");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@test.com",
                                  "password": "12345678"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("로그인 성공 시 Refresh Token이 DB에 저장된다")
    void login_saveRefreshToken() throws Exception {
        signup("user@test.com", "12345678");

        TokenPair tokenPair = login("user@test.com", "12345678");

        Member member = memberRepository.findByEmail("user@test.com").orElseThrow();
        assertThat(member.getRefreshToken()).isEqualTo(tokenPair.refreshToken());
    }

    @Test
    @DisplayName("Access Token으로 내 정보 조회에 성공한다")
    void me_success() throws Exception {
        signup("user@test.com", "12345678");
        TokenPair tokenPair = login("user@test.com", "12345678");

        mockMvc.perform(get("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPair.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("user@test.com"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("Access Token 없이 보호 API에 접근하면 401을 반환한다")
    void me_withoutToken_fail() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @Test
    @DisplayName("USER 토큰으로 ADMIN API에 접근하면 403을 반환한다")
    void adminApi_userToken_fail() throws Exception {
        signup("user@test.com", "12345678");
        TokenPair tokenPair = login("user@test.com", "12345678");

        mockMvc.perform(get("/api/admin/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPair.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("ADMIN 토큰으로 ADMIN API 접근에 성공한다")
    void adminApi_adminToken_success() throws Exception {
        memberRepository.save(new Member(
                "admin@test.com",
                passwordEncoder.encode("12345678"),
                Role.ADMIN
        ));

        TokenPair tokenPair = login("admin@test.com", "12345678");

        mockMvc.perform(get("/api/admin/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPair.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("admin@test.com"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("Refresh Token으로 Access Token과 Refresh Token을 재발급한다")
    void reissue_success() throws Exception {
        signup("user@test.com", "12345678");
        TokenPair tokenPair = login("user@test.com", "12345678");

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(tokenPair.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("재발급 성공 시 DB의 Refresh Token이 새 값으로 갱신된다")
    void reissue_updateRefreshTokenInDb() throws Exception {
        signup("user@test.com", "12345678");
        TokenPair oldTokenPair = login("user@test.com", "12345678");

        TokenPair newTokenPair = reissue(oldTokenPair.refreshToken());

        Member member = memberRepository.findByEmail("user@test.com").orElseThrow();
        assertThat(member.getRefreshToken()).isEqualTo(newTokenPair.refreshToken());
    }

    @Test
    @DisplayName("로그아웃하면 DB의 Refresh Token이 제거된다")
    void logout_success_clearRefreshToken() throws Exception {
        signup("user@test.com", "12345678");
        TokenPair tokenPair = login("user@test.com", "12345678");

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPair.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Member member = memberRepository.findByEmail("user@test.com").orElseThrow();
        assertThat(member.getRefreshToken()).isNull();
    }

    @Test
    @DisplayName("로그아웃 후 기존 Refresh Token으로 재발급하면 실패한다")
    void reissue_afterLogout_fail() throws Exception {
        signup("user@test.com", "12345678");
        TokenPair tokenPair = login("user@test.com", "12345678");

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPair.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(tokenPair.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Refresh Token이 유효하지 않습니다."));
    }

    private void signup(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk());
    }

    private TokenPair login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return extractTokenPair(result);
    }

    private TokenPair reissue(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/reissue")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        return extractTokenPair(result);
    }

    private TokenPair extractTokenPair(MvcResult result) throws Exception {
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data");

        return new TokenPair(
                data.path("accessToken").asText(),
                data.path("refreshToken").asText()
        );
    }

    private record TokenPair(
            String accessToken,
            String refreshToken
    ) {
    }
}