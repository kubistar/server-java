package kr.hhplus.be.server.queue.controller;

import kr.hhplus.be.server.common.ApiResponse;
import kr.hhplus.be.server.queue.domain.QueueToken;
import kr.hhplus.be.server.queue.dto.QueueTokenRequestDto;
import kr.hhplus.be.server.queue.dto.QueueTokenResponseDto;
import kr.hhplus.be.server.queue.exception.QueueTokenExpiredException;
import kr.hhplus.be.server.queue.exception.QueueTokenNotFoundException;
import kr.hhplus.be.server.queue.service.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 대기열 관리 API 컨트롤러
 * 디바이스 핑거프린트 기반 보안 기능 포함
 */
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private static final Logger log = LoggerFactory.getLogger(QueueController.class);

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    /**
     * 디바이스 핑거프린트 기반 대기열 토큰 발급
     * POST /api/queue/token
     *
     * @param request 토큰 발급 요청 DTO
     * @param httpRequest HTTP 요청 객체 (디바이스 정보 추출용)
     * @return 발급된 토큰 정보와 API 응답
     */
    @PostMapping("/token")
    public ResponseEntity<ApiResponse<QueueTokenResponseDto>> issueToken(
            @RequestBody QueueTokenRequestDto request,
            HttpServletRequest httpRequest) {

        String userId = request.getUserId();
        log.info("대기열 토큰 발급 API 호출: userId={}", userId);

        // 디바이스 핑거프린트 생성
        String deviceFingerprint = request.getDeviceFingerprint();
        if (deviceFingerprint == null || deviceFingerprint.trim().isEmpty()) {
            deviceFingerprint = generateDeviceFingerprint(httpRequest);
        }

        String sessionId = httpRequest.getSession().getId();

        try {
            // 간소화된 토큰 발급
            QueueToken queueToken = queueService.issueTokenWithSession(
                    userId, sessionId, deviceFingerprint
            );

            QueueTokenResponseDto response = QueueTokenResponseDto.from(queueToken);

            log.info("대기열 토큰 발급 완료: userId={}, token={}, status={}",
                    userId, queueToken.getToken(), queueToken.getStatus());

            return ResponseEntity.status(201)
                    .body(ApiResponse.created(response, "대기열 토큰이 발급되었습니다."));

        } catch (RuntimeException e) {
            log.warn("대기열 토큰 발급 실패: userId={}, error={}", userId, e.getMessage());

            return ResponseEntity.status(429)
                    .body(ApiResponse.error(429, "QUEUE_ACCESS_DENIED", e.getMessage()));
        }
    }

    /**
     * 디바이스 검증을 포함한 대기열 상태 조회
     * GET /api/queue/status
     *
     * @param authHeader Authorization 헤더 (Bearer 토큰)
     * @param httpRequest HTTP 요청 객체 (디바이스 검증용)
     * @return 대기열 상태 정보와 API 응답
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<QueueTokenResponseDto>> getQueueStatus(
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest httpRequest) {

        String token = extractToken(authHeader);
        log.info("대기열 상태 조회 API 호출: token={}", token);

        // 디바이스 핑거프린트 생성 (검증용)
        String deviceFingerprint = generateDeviceFingerprint(httpRequest);
        String sessionId = httpRequest.getSession().getId();

        try {
            // 간소화된 상태 조회
            QueueToken queueToken = queueService.getQueueStatusWithSession(
                    token, sessionId, deviceFingerprint
            );

            QueueTokenResponseDto response = QueueTokenResponseDto.from(queueToken);

            log.info("대기열 상태 조회 완료: token={}, status={}, position={}",
                    token, queueToken.getStatus(), queueToken.getQueuePosition());

            return ResponseEntity.ok(
                    ApiResponse.success(response, "대기열 상태 조회 성공")
            );

        } catch (QueueTokenNotFoundException | QueueTokenExpiredException e) {
            log.warn("대기열 상태 조회 실패: token={}, error={}", token, e.getMessage());
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "QUEUE_TOKEN_INVALID", e.getMessage()));

        } catch (Exception e) {
            log.error("대기열 상태 조회 중 오류: token={}", token, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error(500, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
        }
    }

    /**
     * 토큰 유효성 검증 API (선택적)
     * POST /api/queue/validate
     *
     * @param authHeader Authorization 헤더 (Bearer 토큰)
     * @return 토큰 유효성 결과
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Boolean>> validateToken(
            @RequestHeader("Authorization") String authHeader) {

        String token = extractToken(authHeader);
        log.info("토큰 유효성 검증 API 호출: token={}", token);

        try {
            boolean isValid = queueService.validateActiveToken(token);

            log.info("토큰 유효성 검증 완료: token={}, valid={}", token, isValid);

            if (isValid) {
                return ResponseEntity.ok(
                        ApiResponse.success(true, "유효한 토큰입니다.")
                );
            } else {
                return ResponseEntity.status(401)
                        .body(ApiResponse.error(401, "QUEUE_TOKEN_INVALID", "유효하지 않은 토큰입니다."));
            }

        } catch (Exception e) {
            log.warn("토큰 검증 실패: token={}, error={}", token, e.getMessage());
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "QUEUE_TOKEN_VALIDATION_FAILED", "토큰 검증에 실패했습니다."));
        }
    }

    /**
     * HTTP 요청에서 디바이스 핑거프린트 생성
     * clientIp, userAgent, 기타 헤더 정보를 조합하여 생성
     *
     * @param request HTTP 요청 객체
     * @return 생성된 디바이스 핑거프린트
     */
    private String generateDeviceFingerprint(HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String acceptLanguage = request.getHeader("Accept-Language");
        String acceptEncoding = request.getHeader("Accept-Encoding");

        String combined = clientIp + "|" +
                (userAgent != null ? userAgent : "unknown") + "|" +
                (acceptLanguage != null ? acceptLanguage : "unknown") + "|" +
                (acceptEncoding != null ? acceptEncoding : "unknown");

        return Integer.toHexString(combined.hashCode());
    }

    /**
     * 실제 클라이언트 IP 추출 (프록시 환경 고려)
     *
     * @param request HTTP 요청 객체
     * @return 실제 클라이언트 IP 주소
     */
    private String getClientIp(HttpServletRequest request) {
        // X-Forwarded-For 헤더 확인 (로드밸런서, 프록시 환경)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // 여러 IP가 있는 경우 첫 번째가 실제 클라이언트 IP
            return xForwardedFor.split(",")[0].trim();
        }

        // X-Real-IP 헤더 확인 (Nginx 등)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // Cloudflare CF-Connecting-IP
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
            return cfConnectingIp;
        }

        // 직접 연결인 경우
        return request.getRemoteAddr();
    }

    /**
     * Authorization 헤더에서 토큰 추출
     *
     * @param authHeader Authorization 헤더 값
     * @return 추출된 토큰
     * @throws IllegalArgumentException 헤더 형식이 잘못된 경우
     */
    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("유효하지 않은 Authorization 헤더입니다.");
        }
        return authHeader.substring(7);
    }
}