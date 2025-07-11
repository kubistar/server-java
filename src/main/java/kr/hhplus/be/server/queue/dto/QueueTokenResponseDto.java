/**
 * 대기열 토큰 응답 DTO
 */
package kr.hhplus.be.server.queue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import kr.hhplus.be.server.queue.domain.QueueToken;

import java.time.LocalDateTime;

public class QueueTokenResponseDto {

    /** 대기열 토큰 */
    @JsonProperty("token")
    private String token;

    /** 사용자 ID */
    @JsonProperty("userId")
    private String userId;

    /** 대기열 위치 */
    @JsonProperty("queuePosition")
    private Long queuePosition;

    /** 예상 대기 시간(분) */
    @JsonProperty("estimatedWaitTimeMinutes")
    private Integer estimatedWaitTimeMinutes;

    /** 토큰 상태 */
    @JsonProperty("status")
    private String status;

    /** 상태 설명 */
    @JsonProperty("statusDescription")
    private String statusDescription;

    /** 토큰 발급 시간 */
    @JsonProperty("issuedAt")
    private LocalDateTime issuedAt;

    /** 토큰 만료 시간 */
    @JsonProperty("expiresAt")
    private LocalDateTime expiresAt;

    /**
     * 기본 생성자
     */
    public QueueTokenResponseDto() {}

    /**
     * QueueToken 도메인 객체로부터 DTO를 생성합니다.
     *
     * @param queueToken 대기열 토큰 도메인 객체
     * @return 생성된 응답 DTO
     */
    public static QueueTokenResponseDto from(QueueToken queueToken) {
        QueueTokenResponseDto dto = new QueueTokenResponseDto();
        dto.token = queueToken.getToken();
        dto.userId = queueToken.getUserId();
        dto.queuePosition = queueToken.getQueuePosition();
        dto.estimatedWaitTimeMinutes = queueToken.getEstimatedWaitTimeMinutes();
        dto.status = queueToken.getStatus().name();
        dto.statusDescription = queueToken.getStatus().getDescription();
        dto.issuedAt = queueToken.getIssuedAt();
        dto.expiresAt = queueToken.getExpiresAt();
        return dto;
    }

    // Getter/Setter 메서드들

    /**
     * 대기열 토큰을 반환합니다.
     *
     * @return 대기열 토큰
     */
    public String getToken() {
        return token;
    }

    /**
     * 대기열 토큰을 설정합니다.
     *
     * @param token 대기열 토큰
     */
    public void setToken(String token) {
        this.token = token;
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
     * 사용자 ID를 설정합니다.
     *
     * @param userId 사용자 ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * 대기열 위치를 반환합니다.
     *
     * @return 대기열 위치
     */
    public Long getQueuePosition() {
        return queuePosition;
    }

    /**
     * 대기열 위치를 설정합니다.
     *
     * @param queuePosition 대기열 위치
     */
    public void setQueuePosition(Long queuePosition) {
        this.queuePosition = queuePosition;
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
     * 예상 대기 시간을 설정합니다.
     *
     * @param estimatedWaitTimeMinutes 예상 대기 시간(분)
     */
    public void setEstimatedWaitTimeMinutes(Integer estimatedWaitTimeMinutes) {
        this.estimatedWaitTimeMinutes = estimatedWaitTimeMinutes;
    }

    /**
     * 토큰 상태를 반환합니다.
     *
     * @return 토큰 상태
     */
    public String getStatus() {
        return status;
    }

    /**
     * 토큰 상태를 설정합니다.
     *
     * @param status 토큰 상태
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 상태 설명을 반환합니다.
     *
     * @return 상태 설명
     */
    public String getStatusDescription() {
        return statusDescription;
    }

    /**
     * 상태 설명을 설정합니다.
     *
     * @param statusDescription 상태 설명
     */
    public void setStatusDescription(String statusDescription) {
        this.statusDescription = statusDescription;
    }

    /**
     * 토큰 발급 시간을 반환합니다.
     *
     * @return 토큰 발급 시간
     */
    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    /**
     * 토큰 발급 시간을 설정합니다.
     *
     * @param issuedAt 토큰 발급 시간
     */
    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    /**
     * 토큰 만료 시간을 반환합니다.
     *
     * @return 토큰 만료 시간
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    /**
     * 토큰 만료 시간을 설정합니다.
     *
     * @param expiresAt 토큰 만료 시간
     */
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public String toString() {
        return "QueueTokenResponseDto{" +
                "token='" + token + '\'' +
                ", userId='" + userId + '\'' +
                ", queuePosition=" + queuePosition +
                ", estimatedWaitTimeMinutes=" + estimatedWaitTimeMinutes +
                ", status='" + status + '\'' +
                ", statusDescription='" + statusDescription + '\'' +
                ", issuedAt=" + issuedAt +
                ", expiresAt=" + expiresAt +
                '}';
    }
}