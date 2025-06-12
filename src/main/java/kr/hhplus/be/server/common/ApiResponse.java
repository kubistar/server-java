package kr.hhplus.be.server.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import kr.hhplus.be.server.common.exception.ErrorResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * API 공통 응답 클래스
 * 모든 REST API의 응답 형식을 통일하기 위한 래퍼 클래스
 */
@JsonInclude(JsonInclude.Include.NON_NULL)   // null 값인 필드는 JSON에서 제외
@Getter
public class ApiResponse<T> {
    /** HTTP 상태 코드 (200, 201, 400, 404, 500 등)*/
    private int code;
    /** 성공 시 반환할 실제 데이터 */
    private T data;
    /** 성공 시 응답 메시지 */
    private String message;
    /** 실패 시 에러 정보 */
    private ErrorDetails error;
    /** 응답 생성 시간 */
    private LocalDateTime timestamp;

    /**
     * 기본 생성자 (외부에서 직접 생성 방지)
     * 현재 시간을 timestamp로 자동 설정
     */
    private ApiResponse() {
        this.timestamp = LocalDateTime.now();
    }

    private ApiResponse(int code, T data, ErrorResponse error) {
        this.code = code;
        this.data = data;
        this.error = error != null
                ? new ErrorDetails(error.getType(), error.getMessage(), error.getDetails())
                : null;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 성공 응답 생성 (HTTP 200)
     * @param data 응답 데이터
     * @param message 성공 메시지
     * @return 성공 응답 객체
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = 200;
        response.data = data;
        response.message = message;
        return response;
    }

    /**
     * 생성 성공 응답 생성 (HTTP 201)
     * @param data 생성된 리소스 데이터
     * @param message 생성 성공 메시지
     * @return 생성 성공 응답 객체
     */
    public static <T> ApiResponse<T> created(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = 201;
        response.data = data;
        response.message = message;
        return response;
    }

    /**
     * 에러 응답 생성 (상세 정보 없음)
     * @param code HTTP 상태 코드
     * @param type 에러 타입 (예: "CONCERT_NOT_FOUND")
     * @param message 에러 메시지
     * @return 에러 응답 객체
     */
    public static <T> ApiResponse<T> error(int code, String type, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = code;
        response.error = new ErrorDetails(type, message, null);
        return response;
    }

    /**
     * 에러 응답 생성 (상세 정보 포함)
     * @param code HTTP 상태 코드
     * @param type 에러 타입
     * @param message 에러 메시지
     * @param details 에러 상세 정보 (예: validation 실패 필드 목록)
     * @return 에러 응답 객체
     */
    public static <T> ApiResponse<T> error(int code, String type, String message, Object details) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = code;
        response.error = new ErrorDetails(type, message, details);
        return response;
    }

    public static <T> ApiResponse<T> error(int status, ErrorResponse error) {
        return new ApiResponse<>(status, null, error);
    }


    /**
     * 에러 상세 정보를 담는 내부 클래스
     */
    @Getter
    @AllArgsConstructor
    public static class ErrorDetails {
        // 에러 유형 (예: "VALIDATION_FAILED", "RESOURCE_NOT_FOUND") */
        private String type;
        // 사용자에게 표시할 에러 메시지 */
        private String message;
        // 에러 상세 정보 (예: 유효성 검사 실패 필드 목록) */
        private Object details;
    }
}