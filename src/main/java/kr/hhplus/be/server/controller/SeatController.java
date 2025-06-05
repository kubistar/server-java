package kr.hhplus.be.server.controller;

import kr.hhplus.be.server.common.ApiResponse;
import kr.hhplus.be.server.dto.SeatPageResponse;
import kr.hhplus.be.server.service.QueueService;
import kr.hhplus.be.server.service.SeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 좌석 조회 API 컨트롤러
 * 대기열 토큰 검증이 필요한 보호된 리소스
 */
@RestController
@RequestMapping("/api/concerts")
public class SeatController {

    private static final Logger log = LoggerFactory.getLogger(SeatController.class);

    private final SeatService seatService;
    private final QueueService queueService;

    public SeatController(SeatService seatService, QueueService queueService) {
        this.seatService = seatService;
        this.queueService = queueService;
    }

    /**
     * 좌석 정보 조회 (대기열 토큰 필요)
     * GET /api/concerts/{concertId}/seats
     */
    @GetMapping("/{concertId}/seats")
    public ResponseEntity<ApiResponse<SeatPageResponse>> getConcertSeats(
            @PathVariable Long concertId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // Authorization 헤더 확인
        if (authHeader == null) {
            log.warn("Authorization 헤더가 누락되었습니다");
            return ResponseEntity.status(400)
                    .body(ApiResponse.error(400, "MISSING_AUTH_HEADER", "Authorization 헤더가 필요합니다."));
        }

        String token = extractToken(authHeader);

        // 토큰 형식 검증
        if (token == null) {
            log.warn("유효하지 않은 Authorization 헤더 형식: {}", authHeader);
            return ResponseEntity.status(400)
                    .body(ApiResponse.error(400, "INVALID_AUTH_FORMAT", "Bearer 토큰 형식이 아닙니다."));
        }

        log.info("좌석 조회 API 호출: concertId={}, token={}", concertId, token);

        // 대기열 토큰 검증 추가!
        if (!queueService.validateActiveToken(token)) {
            log.warn("유효하지 않은 대기열 토큰: {}", token);
            return ResponseEntity.status(403)
                    .body(ApiResponse.error(403, "INVALID_TOKEN", "유효하지 않은 대기열 토큰입니다."));
        }

        SeatPageResponse seatInfo = seatService.getConcertSeats(concertId);

        log.info("좌석 조회 완료: concertId={}, totalSeats={}, availableSeats={}",
                concertId, seatInfo.getSummary().getTotalSeats(), seatInfo.getSummary().getAvailableSeats());

        return ResponseEntity.ok(
                ApiResponse.success(seatInfo, "좌석 정보 조회 성공")
        );
    }

    /**
     * Authorization 헤더에서 토큰 추출
     */
    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null; // 예외를 던지지 않고 null 반환
        }
        return authHeader.substring(7);
    }
}