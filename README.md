# JWT Auth

Spring Security와 JWT를 사용해 인증/인가 흐름을 구현한 미니 프로젝트입니다.

단순 로그인 기능 구현이 아니라, Access Token과 Refresh Token을 분리하고 Refresh Token을 서버에서 관리하여 재발급과 로그아웃 흐름까지 검증하는 것을 목표로 했습니다.

## 프로젝트 목표

* Spring Security 기반 인증/인가 흐름 이해
* JWT 발급, 검증, 인증 객체 저장 흐름 구현
* Access Token / Refresh Token 분리
* Refresh Token 재발급 및 로그아웃 처리
* 권한별 API 접근 제어
* 공통 응답 및 예외 처리 구조 적용

## 기술 스택

* Java 21
* Spring Boot 3.x
* Spring Security
* Spring Data JPA
* H2 Database
* JWT
* Gradle
* Lombok

## 주요 기능

### 인증

* 회원가입
* 로그인
* Access Token 발급
* Refresh Token 발급
* Refresh Token 기반 Access Token 재발급
* 로그아웃

### 인가

* 인증 사용자 전용 API
* ADMIN 권한 전용 API
* USER 토큰으로 ADMIN API 접근 시 403 응답

### 공통 처리

* 공통 응답 형식
* 비즈니스 예외 처리
* 인증 실패 401 응답
* 인가 실패 403 응답
* Validation 적용

## 인증 흐름

### 1. 로그인

사용자가 이메일과 비밀번호로 로그인하면 서버는 Access Token과 Refresh Token을 발급합니다.

```text
로그인 요청
→ 이메일 조회
→ 비밀번호 검증
→ Access Token 발급
→ Refresh Token 발급
→ Refresh Token DB 저장
→ 토큰 응답
```

Access Token은 API 접근에 사용하고, Refresh Token은 Access Token 재발급에 사용합니다.

### 2. API 요청 인증

클라이언트는 보호 API 요청 시 Authorization Header에 Access Token을 전달합니다.

```text
Authorization: Bearer {accessToken}
```

서버는 JwtAuthenticationFilter에서 토큰을 검증한 뒤, 인증 정보를 SecurityContext에 저장합니다.

```text
요청
→ JwtAuthenticationFilter
→ Authorization Header 확인
→ JWT 검증
→ memberId, email, role 추출
→ Authentication 객체 생성
→ SecurityContext 저장
→ Controller 접근
```

### 3. Access Token 재발급

Access Token이 만료되면 Refresh Token으로 새 토큰을 발급받습니다.

```text
재발급 요청
→ Refresh Token 유효성 검증
→ 토큰에서 memberId 추출
→ DB 회원 조회
→ DB에 저장된 Refresh Token과 요청 Refresh Token 비교
→ Access Token 재발급
→ Refresh Token 재발급
→ 새 Refresh Token DB 갱신
```

Refresh Token은 재발급 시마다 새 값으로 교체합니다.
이 방식은 이전 Refresh Token의 재사용 가능성을 줄이기 위한 선택입니다.

### 4. 로그아웃

로그아웃 요청은 Access Token 인증이 필요합니다.

```text
로그아웃 요청
→ Access Token 검증
→ 현재 사용자 식별
→ DB의 Refresh Token 제거
```

JWT는 발급된 Access Token 자체를 서버에서 즉시 폐기하기 어렵습니다.
따라서 로그아웃 시 Refresh Token을 서버에서 제거하여 이후 재발급 경로를 차단했습니다.

## API 명세

### 회원가입

```http
POST /api/auth/signup
Content-Type: application/json
```

Request

```json
{
  "email": "test@test.com",
  "password": "12345678"
}
```

Response

```json
{
  "success": true,
  "data": null,
  "message": null
}
```

### 로그인

```http
POST /api/auth/login
Content-Type: application/json
```

Request

```json
{
  "email": "test@test.com",
  "password": "12345678"
}
```

Response

```json
{
  "success": true,
  "data": {
    "accessToken": "...",
    "refreshToken": "...",
    "tokenType": "Bearer"
  },
  "message": null
}
```

### 토큰 재발급

```http
POST /api/auth/reissue
Content-Type: application/json
```

Request

```json
{
  "refreshToken": "..."
}
```

Response

```json
{
  "success": true,
  "data": {
    "accessToken": "...",
    "refreshToken": "...",
    "tokenType": "Bearer"
  },
  "message": null
}
```

### 로그아웃

```http
POST /api/auth/logout
Authorization: Bearer {accessToken}
```

Response

```json
{
  "success": true,
  "data": null,
  "message": null
}
```

### 내 정보 조회

```http
GET /api/members/me
Authorization: Bearer {accessToken}
```

Response

```json
{
  "success": true,
  "data": {
    "memberId": 1,
    "email": "test@test.com",
    "role": "USER"
  },
  "message": null
}
```

### ADMIN API 테스트

```http
GET /api/admin/test
Authorization: Bearer {adminAccessToken}
```

USER 권한 토큰으로 요청하면 403 응답이 발생합니다.

## 보안 설계 포인트

### 1. Stateless Session

Spring Security 세션을 사용하지 않고 JWT 기반 인증 구조로 구성했습니다.

```java
.sessionManagement(session ->
    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
)
```

서버는 Access Token으로 사용자를 식별하고, 매 요청마다 JWT를 검증합니다.

### 2. Access Token / Refresh Token 분리

Access Token은 API 접근에 사용하고, Refresh Token은 재발급에만 사용합니다.

분리 이유는 다음과 같습니다.

* Access Token 만료 시간을 짧게 가져갈 수 있음
* 사용자는 Refresh Token으로 다시 로그인 없이 재발급 가능
* 로그아웃 시 Refresh Token을 제거해 재발급 경로 차단 가능
* 토큰 탈취 리스크를 역할별로 분리 가능

### 3. Refresh Token 서버 저장

Refresh Token을 클라이언트에만 맡기지 않고 서버 DB에도 저장했습니다.

재발급 요청 시 다음 두 조건을 모두 확인합니다.

```text
1. Refresh Token 자체가 유효한가
2. DB에 저장된 Refresh Token과 일치하는가
```

이를 통해 로그아웃 처리와 Refresh Token 회전 전략을 구현할 수 있습니다.

### 4. Refresh Token Rotation

재발급 성공 시 Access Token만 새로 발급하지 않고 Refresh Token도 함께 새로 발급합니다.

```text
기존 Refresh Token
→ 재발급 성공
→ 새 Refresh Token 저장
→ 기존 Refresh Token 재사용 차단
```

### 5. 인증 실패와 인가 실패 분리

인증 실패와 인가 실패를 분리해 처리했습니다.

* 401 Unauthorized: 인증 정보가 없거나 유효하지 않은 경우
* 403 Forbidden: 인증은 되었지만 권한이 부족한 경우

이를 위해 Spring Security의 AuthenticationEntryPoint와 AccessDeniedHandler를 각각 구현했습니다.

## 예외 응답 구조

공통 응답 형식은 다음과 같습니다.

```json
{
  "success": false,
  "data": null,
  "message": "에러 메시지"
}
```

주요 예외 상황:

* 중복 이메일 회원가입
* 로그인 실패
* 인증되지 않은 요청
* 권한 없는 요청
* 유효하지 않은 Refresh Token
* 로그아웃 후 재발급 요청

## 테스트한 시나리오

### 회원가입

* 신규 이메일 회원가입 성공
* 중복 이메일 회원가입 실패

### 로그인

* 올바른 이메일/비밀번호 로그인 성공
* Access Token 발급 확인
* Refresh Token 발급 확인
* Refresh Token DB 저장 확인

### 인증 API

* Access Token으로 `/api/members/me` 접근 성공
* Access Token 없이 보호 API 접근 시 401 응답

### 인가 API

* USER 토큰으로 ADMIN API 접근 시 403 응답
* ADMIN 토큰으로 ADMIN API 접근 시 200 응답

### 재발급

* 유효한 Refresh Token으로 Access Token 재발급 성공
* 재발급 성공 시 Refresh Token도 새로 발급
* 이전 Refresh Token 재사용 실패

### 로그아웃

* Access Token으로 로그아웃 성공
* 로그아웃 시 DB Refresh Token 제거
* 로그아웃 후 기존 Refresh Token으로 재발급 실패

## 테스트

MockMvc 기반 통합 테스트를 작성하여 JWT 인증/인가 흐름을 검증했다.

검증 항목:
- 회원가입 성공 및 중복 이메일 예외
- 로그인 시 Access Token / Refresh Token 발급
- Access Token 기반 보호 API 접근
- Refresh Token 재발급 및 DB 저장값 갱신
- 로그아웃 시 Refresh Token 제거
- 로그아웃 후 기존 Refresh Token 재사용 실패
- USER 권한의 ADMIN API 접근 실패
- ADMIN 권한의 ADMIN API 접근 성공

## 테스트 실행 방법

전체 테스트는 다음 명령어로 실행할 수 있습니다.

./gradlew test

Windows 환경에서는 다음 명령어를 사용할 수 있습니다.

gradlew.bat test

MockMvc 기반 통합 테스트를 통해 Spring Security Filter Chain, JWT 검증, SecurityContext 저장, 권한 검증, Refresh Token 재발급, 로그아웃 흐름을 검증했습니다.

## 실행 방법

```bash
git clone https://github.com/kdy2285/jwt-auth.git
cd jwt-auth
./gradlew bootRun
```

Windows 환경에서는 다음 명령어를 사용할 수 있습니다.

```bash
gradlew.bat bootRun
```

## H2 Console

```text
http://localhost:8080/h2-console
```

기본 설정:

```text
JDBC URL: jdbc:h2:mem:jwt_auth
User Name: sa
Password:
```

## 주요 구현 클래스

```text
auth
- AuthController
- AuthService
- SignupRequest
- LoginRequest
- RefreshTokenRequest
- TokenResponse

domain.member
- Member
- Role
- MemberRepository

security.jwt
- JwtTokenProvider
- JwtAuthenticationFilter
- JwtPrincipal

security.handler
- JwtAuthenticationEntryPoint
- JwtAccessDeniedHandler

common
- ApiResponse
- BusinessException
- ErrorCode
- GlobalExceptionHandler
```

## 기술적 의사결정

### 세션 대신 JWT를 사용한 이유

API 서버 구조에서 서버 세션 의존도를 줄이고, 클라이언트가 토큰을 전달하는 방식으로 인증 상태를 처리하기 위해 JWT를 사용했습니다.

다만 JWT는 발급 후 Access Token을 서버에서 즉시 폐기하기 어렵기 때문에, Refresh Token을 서버 DB에 저장하고 로그아웃 시 제거하는 방식으로 보완했습니다.

### Refresh Token을 DB에 저장한 이유

Refresh Token을 서버에서 관리하지 않으면 로그아웃이나 강제 만료 처리가 어렵습니다.

따라서 Refresh Token을 DB에 저장하고, 재발급 요청 시 DB 저장값과 비교하도록 구현했습니다.

이를 통해 다음 처리가 가능해졌습니다.

* 로그아웃 시 Refresh Token 제거
* 탈취 의심 시 Refresh Token 무효화
* 재발급 시 Refresh Token Rotation 적용
* 이전 Refresh Token 재사용 차단

### 로그아웃을 Access Token 기반으로 처리한 이유

로그아웃은 현재 로그인한 사용자의 Refresh Token을 제거하는 작업입니다.

따라서 요청자의 신원을 식별하기 위해 Access Token 인증을 먼저 수행하고, SecurityContext에 저장된 사용자 정보를 기준으로 Refresh Token을 제거했습니다.

## 한계와 개선 방향

현재 프로젝트는 JWT 인증/인가 흐름 학습을 위한 미니 프로젝트입니다. 실제 운영 환경에서는 다음 보완이 필요합니다.

* Refresh Token 저장소를 Redis로 분리
* Redis TTL 기반 Refresh Token 만료 관리
* Access Token Blacklist 도입 검토
* Token Version 기반 강제 로그아웃 구조 검토
* Refresh Token 탈취 탐지
* HTTPS 적용
* Cookie 기반 Refresh Token 전달 검토
* SameSite, HttpOnly, Secure 옵션 적용
* 운영 DB 환경 구성
* Testcontainers 기반 운영 DB 유사 환경 테스트 보강

## 면접 답변 요약

이 프로젝트에서는 Spring Security 기반 JWT 인증/인가 흐름을 구현했습니다.

로그인 시 Access Token과 Refresh Token을 발급하고, Access Token은 API 접근에 사용하며 Refresh Token은 재발급에만 사용하도록 분리했습니다.

JWT는 발급된 Access Token을 서버에서 즉시 폐기하기 어렵기 때문에 Refresh Token을 DB에 저장했습니다. 로그아웃 시 DB의 Refresh Token을 제거하여 이후 재발급 경로를 차단했습니다.

또한 Refresh Token 재발급 시 새 Refresh Token으로 교체하는 Rotation 방식을 적용해 기존 Refresh Token의 재사용 가능성을 줄였습니다.

권한 처리는 Spring Security Filter Chain에서 수행되며, USER 토큰으로 ADMIN API 접근 시 403 응답이 발생하도록 인증과 인가 실패를 분리했습니다.
