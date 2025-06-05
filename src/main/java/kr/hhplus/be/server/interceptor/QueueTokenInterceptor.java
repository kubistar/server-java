package kr.hhplus.be.server.interceptor;

import kr.hhplus.be.server.service.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 대기열 토큰 검증 인터셉터
 * 좌석 조회 등 보호된 리소스 접근 시 토큰 검증
 */
@Component
public class QueueTokenInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(QueueTokenInterceptor.class);

    private final QueueService queueService;

    public QueueTokenInterceptor(QueueService queueService) {
        this.queueService = queueService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        log.info("대기열 토큰 검증 시작: {} {}", method, requestURI);

        // OPTIONS 요청은 통과
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // Authorization 헤더에서 토큰 추출
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Authorization 헤더 누락 또는 형식 오류: {}", authHeader);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(createErrorResponse("MISSING_TOKEN", "대기열 토큰이 필요합니다."));
            return false;
        }

        String token = authHeader.substring(7);

        // 토큰 유효성 검증
        boolean isValid = queueService.validateActiveToken(token);

        if (!isValid) {
            log.warn("유효하지 않은 토큰: token={}", token);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(createErrorResponse("INVALID_TOKEN", "유효하지 않은 토큰이거나 대기열에서 순서를 기다려주세요."));
            return false;
        }

        log.info("대기열 토큰 검증 성공: token={}", token);
        return true;
    }

    /**
     * 에러 응답 JSON 생성
     */
    private String createErrorResponse(String type, String message) {
        return String.format(
                "{\"code\":%d,\"error\":{\"type\":\"%s\",\"message\":\"%s\"},\"timestamp\":\"%s\"}",
                403, type, message, java.time.LocalDateTime.now()
        );
    }
}