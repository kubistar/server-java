package kr.hhplus.be.server.exception;

import lombok.Getter;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
public class ErrorResponse {
    private final String type;
    private final String message;
    private final Map<String, Object> details;
    private final LocalDateTime timestamp;

    public ErrorResponse(String type, String message, Map<String, Object> details) {
        this.type = type;
        this.message = message;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }
}
