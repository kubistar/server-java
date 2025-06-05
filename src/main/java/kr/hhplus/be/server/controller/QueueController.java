package kr.hhplus.be.server.controller;

import kr.hhplus.be.server.common.ApiResponse;
import kr.hhplus.be.server.domain.queue.QueueToken;
import kr.hhplus.be.server.dto.QueueTokenRequestDto;
import kr.hhplus.be.server.dto.QueueTokenResponseDto;
import kr.hhplus.be.server.service.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 대기열 관리 API 컨트롤러
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
     * 대기열 토큰 발급
     * POST /api/queue/token
     */
    @PostMapping("/token")
    public ResponseEntity<ApiResponse<QueueTokenResponseDto>> issueToken(
            @RequestBody QueueTokenRequestDto request) {

        log.info("대기열 토큰 발급 API 호출: userId={}", request.getUserId());

        QueueToken queueToken = queueService.issueToken(request.getUserId());
        QueueTokenResponseDto response = QueueTokenResponseDto.from(queueToken);

        log.info("대기열 토큰 발급 완료: userId={}, token={}, status={}",
                request.getUserId(), queueToken.getToken(), queueToken.getStatus());

        return ResponseEntity.status(201)
                .body(ApiResponse.created(response, "대기열 토큰이 발급되었습니다."));
    }

    /**
     * 대기열 상태 조회
     * GET /api/queue/status
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<QueueTokenResponseDto>> getQueueStatus(
            @RequestHeader("Authorization") String authHeader) {

        String token = extractToken(authHeader);
        log.info("대기열 상태 조회 API 호출: token={}", token);

        QueueToken queueToken = queueService.getQueueStatus(token);
        QueueTokenResponseDto response = QueueTokenResponseDto.from(queueToken);

        log.info("대기열 상태 조회 완료: token={}, status={}, position={}",
                token, queueToken.getStatus(), queueToken.getQueuePosition());

        return ResponseEntity.ok(
                ApiResponse.success(response, "대기열 상태 조회 성공")
        );
    }

    /**
     * Authorization 헤더에서 토큰 추출
     */
    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("유효하지 않은 Authorization 헤더입니다.");
        }
        return authHeader.substring(7);
    }
}