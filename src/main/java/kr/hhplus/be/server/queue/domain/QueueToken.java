package kr.hhplus.be.server.queue.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * 대기열 토큰 도메인 객체
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueueToken {

    /** 고유 토큰 식별자 */
    private String token;

    /** 토큰 소유자의 사용자 ID */
    private String userId;

    /** 대기열에서의 현재 위치 (0부터 시작, null이면 활성 상태) */
    private Long queuePosition;

    /** 예상 대기 시간(분) */
    private Integer estimatedWaitTimeMinutes;

    /** 토큰의 현재 상태 */
    private QueueStatus status;

    /** 토큰 발급 시간 */
    private LocalDateTime issuedAt;

    /** 토큰 만료 시간 */
    private LocalDateTime expiresAt;

    /**
     * 기본 생성자
     *
     * <p>JPA 및 Jackson 직렬화를 위한 기본 생성자입니다.</p>
     */
    protected QueueToken() {}

    /**
     * QueueToken 생성자
     *
     * <p>새로운 대기열 토큰을 생성합니다.</p>
     *
     * @param token 고유 토큰 식별자
     * @param userId 사용자 ID
     * @param queuePosition 대기열 위치 (활성 상태인 경우 null 가능)
     * @param estimatedWaitTimeMinutes 예상 대기 시간(분)
     * @param status 토큰 상태
     * @param issuedAt 발급 시간
     * @param expiresAt 만료 시간
     *
     * @throws IllegalArgumentException token 또는 userId가 null인 경우
     */
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
     * 토큰이 활성 상태인지 확인합니다.
     *
     * <p>토큰이 ACTIVE 상태이면서 만료 시간이 현재 시간보다 이후인 경우에만
     * 활성 상태로 간주됩니다.</p>
     *
     * @return 토큰이 활성 상태이면 true, 그렇지 않으면 false
     */
    public boolean isActive() {
        return status == QueueStatus.ACTIVE && expiresAt.isAfter(LocalDateTime.now());
    }

    /**
     * 토큰이 만료되었는지 확인합니다.
     *
     * <p>만료 시간이 현재 시간보다 이전인 경우 만료된 것으로 판단합니다.</p>
     *
     * @return 토큰이 만료되었으면 true, 그렇지 않으면 false
     */
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    /**
     * 토큰을 활성 상태로 변경합니다.
     *
     * <p>대기열에서 사용자가 서비스에 접근할 수 있는 상태가 되었을 때 호출됩니다.</p>
     */
    public void activate() {
        this.status = QueueStatus.ACTIVE;
    }

    /**
     * 토큰을 만료 상태로 변경합니다.
     *
     * <p>토큰의 유효 시간이 지났거나 명시적으로 만료시킬 때 호출됩니다.</p>
     */
    public void expire() {
        this.status = QueueStatus.EXPIRED;
    }

    /**
     * 대기열 위치와 예상 대기 시간을 업데이트합니다.
     *
     * <p>대기열 상태가 변경될 때마다 호출되어 사용자에게 최신 정보를 제공합니다.</p>
     *
     * @param position 새로운 대기열 위치
     * @param waitTime 새로운 예상 대기 시간(분)
     */
    public void updatePosition(Long position, Integer waitTime) {
        this.queuePosition = position;
        this.estimatedWaitTimeMinutes = waitTime;
    }

    /**
     * 토큰 식별자를 반환합니다.
     *
     * @return 토큰 식별자
     */
    public String getToken() {
        return token;
    }

    /**
     * 사용자 ID를 반환합니다.
     *
     * @return 사용자 ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 대기열 위치를 반환합니다.
     *
     * @return 대기열 위치 (활성 상태인 경우 null)
     */
    public Long getQueuePosition() {
        return queuePosition;
    }

    /**
     * 예상 대기 시간을 반환합니다.
     *
     * @return 예상 대기 시간(분)
     */
    public Integer getEstimatedWaitTimeMinutes() {
        return estimatedWaitTimeMinutes;
    }

    /**
     * 토큰 상태를 반환합니다.
     *
     * @return 토큰 상태 (WAITING, ACTIVE, EXPIRED)
     */
    public QueueStatus getStatus() {
        return status;
    }

    /**
     * 토큰 발급 시간을 반환합니다.
     *
     * @return 발급 시간
     */
    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    /**
     * 토큰 만료 시간을 반환합니다.
     *
     * @return 만료 시간
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}