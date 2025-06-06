package kr.hhplus.be.server.dto;

import java.time.LocalDateTime;
import java.util.Map;

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
    public String getType() { return type; }
    public String getMessage() { return message; }
    public Map<String, Object> getDetails() { return details; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
