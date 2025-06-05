package kr.hhplus.be.server.common;

import kr.hhplus.be.server.exception.ConcertNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리 핸들러
 * - 컨트롤러에서 발생하는 예외들을 한 곳에서 처리
 * - 예외별로 적절한 HTTP 상태코드와 메시지를 반환
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    /**
     * 처리되지 않은 일반적인 예외 처리 (서버 내부 오류 등)
     *
     * @param e 발생한 예외
     * @return 500 응답 + 고정 메시지
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception e) {
        ApiResponse<Void> response = ApiResponse.error(
                500,
                "INTERNAL_SERVER_ERROR",
                "서버 내부 오류가 발생했습니다."  // 사용자에게 노출될 일반 메시지
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
