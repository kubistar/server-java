package kr.hhplus.be.server.dto;

import kr.hhplus.be.server.domain.queue.QueueStatus;
import kr.hhplus.be.server.domain.queue.QueueToken;

import java.time.LocalDateTime;

/**
 * 대기열 토큰 응답 DTO
 */
public class QueueTokenResponseDto {
    private String token;
    private String userId;
    private Long queuePosition;
    private Integer estimatedWaitTimeMinutes;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;

    protected QueueTokenResponseDto() {}

    public static QueueTokenResponseDto from(QueueToken queueToken) {
        QueueTokenResponseDto dto = new QueueTokenResponseDto();
        dto.token = queueToken.getToken();
        dto.userId = queueToken.getUserId();
        dto.queuePosition = queueToken.getQueuePosition();
        dto.estimatedWaitTimeMinutes = queueToken.getEstimatedWaitTimeMinutes();
        dto.status = queueToken.getStatus().name();
        dto.issuedAt = queueToken.getIssuedAt();
        dto.expiresAt = queueToken.getExpiresAt();
        return dto;
    }

    // Getters
    public String getToken() { return token; }
    public String getUserId() { return userId; }
    public Long getQueuePosition() { return queuePosition; }
    public Integer getEstimatedWaitTimeMinutes() { return estimatedWaitTimeMinutes; }
    public String getStatus() { return status; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
