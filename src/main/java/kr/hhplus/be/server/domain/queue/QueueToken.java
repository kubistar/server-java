package kr.hhplus.be.server.domain.queue;

import java.time.LocalDateTime;

/**
 * 대기열 토큰 도메인 객체
 */
public class QueueToken {
    private String token;
    private String userId;
    private Long queuePosition;
    private Integer estimatedWaitTimeMinutes;
    private QueueStatus status;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;

    protected QueueToken() {}

    public QueueToken(String token, String userId, Long queuePosition,
                      Integer estimatedWaitTimeMinutes, QueueStatus status,
                      LocalDateTime issuedAt, LocalDateTime expiresAt) {
        this.token = token;
        this.userId = userId;
        this.queuePosition = queuePosition;
        this.estimatedWaitTimeMinutes = estimatedWaitTimeMinutes;
        this.status = status;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * 토큰이 활성 상태인지 확인
     */
    public boolean isActive() {
        return status == QueueStatus.ACTIVE && expiresAt.isAfter(LocalDateTime.now());
    }

    /**
     * 토큰이 만료되었는지 확인
     */
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    // Getters
    public String getToken() { return token; }
    public String getUserId() { return userId; }
    public Long getQueuePosition() { return queuePosition; }
    public Integer getEstimatedWaitTimeMinutes() { return estimatedWaitTimeMinutes; }
    public QueueStatus getStatus() { return status; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }

    // Status 변경 메서드
    public void activate() {
        this.status = QueueStatus.ACTIVE;
    }

    public void expire() {
        this.status = QueueStatus.EXPIRED;
    }

    public void updatePosition(Long position, Integer waitTime) {
        this.queuePosition = position;
        this.estimatedWaitTimeMinutes = waitTime;
    }
}
