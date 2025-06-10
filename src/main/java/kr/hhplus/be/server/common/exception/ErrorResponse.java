package kr.hhplus.be.server.common.exception;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
public class ErrorResponse {
    private String type;
    private String message;
    private Map<String, Object> details;
    private LocalDateTime timestamp;

    public ErrorResponse(String type, String message, Map<String, Object> details) {
        this.type = type;
        this.message = message;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }

    // Getter 메소드들

}
