package kr.hhplus.be.server.common;

import kr.hhplus.be.server.dto.ErrorResponse;
import kr.hhplus.be.server.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리 핸들러
 * - 컨트롤러에서 발생하는 예외들을 한 곳에서 처리
 * - 예외별로 적절한 HTTP 상태코드와 메시지를 반환
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * ConcertNotFoundException 발생 시 처리
     *
     * @param e 발생한 예외
     * @return 404 응답 + 에러 메시지
     */
    @ExceptionHandler(ConcertNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcertNotFound(ConcertNotFoundException e) {
        ApiResponse<Void> response = ApiResponse.error(
                404,
                "CONCERT_NOT_FOUND",      // 에러 코드
                e.getMessage()            // 예외 메시지를 그대로 사용
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * 잘못된 인자 전달로 인한 IllegalArgumentException 처리
     *
     * @param e 발생한 예외
     * @return 400 응답 + 에러 메시지
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        ApiResponse<Void> response = ApiResponse.error(
                400,
                "INVALID_ARGUMENT",
                e.getMessage()
        );
        return ResponseEntity.badRequest().body(response);
    }


    @ExceptionHandler(SeatNotAvailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleSeatNotAvailable(SeatNotAvailableException e) {
        logger.warn("좌석 예약 불가: {}", e.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("concertId", e.getConcertId());
        details.put("seatNumber", e.getSeatNumber());
        details.put("currentStatus", e.getCurrentStatus());

        ErrorResponse error = new ErrorResponse(
                "SEAT_NOT_AVAILABLE",
                e.getMessage(),
                details
        );

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, error));
    }

    @ExceptionHandler(ConcurrentReservationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrentReservation(ConcurrentReservationException e) {
        logger.warn("동시 예약 충돌: {}", e.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("retryAfterSeconds", e.getRetryAfterSeconds());

        ErrorResponse error = new ErrorResponse(
                "CONCURRENT_RESERVATION_CONFLICT",
                e.getMessage(),
                details
        );

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, error));
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleReservationNotFound(ReservationNotFoundException e) {
        logger.warn("예약 조회 실패: {}", e.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("reservationId", e.getReservationId());

        ErrorResponse error = new ErrorResponse(
                "RESERVATION_NOT_FOUND",
                e.getMessage(),
                details
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, error));
    }

    @ExceptionHandler(ReservationExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleReservationExpired(ReservationExpiredException e) {
        logger.warn("만료된 예약 접근: {}", e.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("reservationId", e.getReservationId());
        details.put("expiredAt", e.getExpiredAt());
        details.put("currentTime", LocalDateTime.now());

        ErrorResponse error = new ErrorResponse(
                "RESERVATION_EXPIRED",
                e.getMessage(),
                details
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception e) {
        logger.error("예상치 못한 오류 발생", e);

        ErrorResponse error = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "서버 내부 오류가 발생했습니다.",
                null
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, error));
    }
}
