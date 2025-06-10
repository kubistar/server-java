package kr.hhplus.be.server.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Swagger/OpenAPI 3.0 설정 클래스
 *
 * 📚 접속 방법:
 * - Swagger UI: http://localhost:8080/swagger-ui/index.html
 * - JSON 문서: http://localhost:8080/v3/api-docs
 *
 * 🔧 사용 버전: springdoc-openapi-starter-webmvc-ui:2.0.0
 *
 * @author 콘서트 예약 서비스 개발팀
 * @since 2025-06-05
 */
@Configuration
public class SwaggerConfig {

    /**
     * OpenAPI 3.0 메인 설정
     *
     * Spring Boot 시작 시 자동으로 로드되어 Swagger UI 생성
     *
     * @return OpenAPI 설정 객체
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                // 📖 API 문서 기본 정보
                .info(createApiInfo())

                // 🌐 서버 환경 정보
                .servers(createServerList())

                // 🔐 보안 스키마 정의
                .components(createSecurityComponents())

                // 🛡️ 전역 보안 설정 적용
                .addSecurityItem(createGlobalSecurityRequirement());
    }

    /**
     * API 문서 기본 정보 설정
     *
     * Swagger UI 상단에 표시되는 제목, 설명, 버전 등
     */
    private Info createApiInfo() {
        return new Info()
                .title("🎵 콘서트 예약 서비스 API")
                .description(buildApiDescription())
                .version("v1.0.0")
                .contact(createContactInfo())
                .license(createLicenseInfo());
    }

    /**
     * API 설명 텍스트 구성
     */
    private String buildApiDescription() {
        return """
                ## 🎯 서비스 개요
                대기열 기반 콘서트 좌석 예약 시스템입니다.
                
                ## 📋 주요 기능
                - 🎫 **대기열 토큰 관리**: 공정한 순서 보장
                - 🎵 **콘서트 정보 조회**: 날짜별 콘서트 및 좌석 정보
                - 🪑 **좌석 예약**: 임시 배정 및 결제 처리
                - 💰 **잔액 관리**: 충전 및 결제 시스템
                
                ## 🔐 API 인증 방식
                
                ### 🔒 인증 필요 API
                다음 API들은 대기열 토큰이 필요합니다:
                - 좌석 예약 요청
                - 결제 처리
                - 잔액 충전
                - 예약 상태 조회
                
                **헤더 형식:** `Authorization: Bearer {queue-token}`
                
                ### 🔓 인증 불필요 API
                다음 API들은 토큰 없이 접근 가능합니다:
                - 대기열 토큰 발급
                - 콘서트 목록 조회
                - 좌석 정보 조회
                - 잔액 조회
                - 결제 내역 조회
                
                ## 🚀 개발 현황
                **현재 단계:** Mock API 구현 완료
                
                **다음 단계:** 실제 비즈니스 로직 구현 예정
                
                ## 📊 HTTP 상태 코드
                - `200` OK: 조회 성공
                - `201` Created: 생성 성공 (토큰 발급, 예약 등)
                - `400` Bad Request: 잘못된 요청 데이터
                - `401` Unauthorized: 인증 실패 (토큰 없음/무효)
                - `403` Forbidden: 권한 없음 (대기열 미통과)
                - `404` Not Found: 리소스 없음
                - `409` Conflict: 충돌 (중복 예약, 동시성 문제)
                - `500` Internal Server Error: 서버 오류
                """;
    }

    /**
     * 개발팀 연락처 정보
     */
    private Contact createContactInfo() {
        return new Contact()
                .name("콘서트 예약 서비스 개발팀")
                .email("dev@concert-reservation.com")
                .url("https://github.com/concert-reservation/api");
    }

    /**
     * 라이선스 정보
     */
    private License createLicenseInfo() {
        return new License()
                .name("MIT License")
                .url("https://opensource.org/licenses/MIT");
    }

    /**
     * 서버 환경 목록 설정
     *
     * Swagger UI에서 요청을 보낼 서버를 선택할 수 있음
     */
    private java.util.List<Server> createServerList() {
        return Arrays.asList(
                new Server()
                        .url("http://localhost:8080")
                        .description("🔧 로컬 개발 서버")
                        .extensions(null),

                new Server()
                        .url("https://dev-api.concert-reservation.com")
                        .description("🚧 개발 환경 서버"),

                new Server()
                        .url("https://staging-api.concert-reservation.com")
                        .description("🧪 스테이징 서버"),

                new Server()
                        .url("https://api.concert-reservation.com")
                        .description("🚀 운영 환경 서버")
        );
    }

    /**
     * 보안 컴포넌트 설정
     *
     * JWT 토큰 방식의 인증 스키마 정의
     */
    private Components createSecurityComponents() {
        SecurityScheme bearerAuthScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("UUID")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description(buildSecurityDescription());

        return new Components()
                .addSecuritySchemes("bearerAuth", bearerAuthScheme);
    }

    /**
     * 보안 인증 설명 텍스트
     */
    private String buildSecurityDescription() {
        return """
                ## 🎫 대기열 토큰 인증
                
                ### 📝 사용 방법
                1. `POST /api/queue/token`으로 토큰 발급
                2. 응답에서 받은 `token` 값을 Authorization 헤더에 포함
                3. 인증이 필요한 API 호출 시 헤더 포함
                
                ### 📋 헤더 형식
                ```
                Authorization: Bearer 550e8400-e29b-41d4-a716-446655440000
                ```
                
                ### ⏰ 토큰 상태
                - **WAITING**: 대기열에서 순서 대기 중
                - **ACTIVE**: 서비스 이용 가능 상태  
                - **EXPIRED**: 토큰 만료 (재발급 필요)
                
                ### 🔒 인증 필요 API 목록
                - 좌석 예약: `POST /api/reservations`
                - 결제 처리: `POST /api/payments`
                - 잔액 충전: `POST /api/users/{userId}/balance`
                - 예약 상태 조회: `GET /api/reservations/{reservationId}`
                - 대기열 상태 조회: `GET /api/queue/status`
                
                ### ⚠️ 주의사항
                - 토큰은 1시간 후 자동 만료
                - 결제 완료 시 토큰 즉시 만료
                - 동시에 하나의 토큰만 유효
                """;
    }

    /**
     * 전역 보안 요구사항 설정
     *
     * 모든 API에 기본적으로 적용되는 보안 설정
     * (개별 API에서 @SecurityRequirement로 재정의 가능)
     */
    private SecurityRequirement createGlobalSecurityRequirement() {
        return new SecurityRequirement()
                .addList("bearerAuth");
    }
}