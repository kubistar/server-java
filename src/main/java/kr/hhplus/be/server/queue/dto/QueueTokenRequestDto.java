package kr.hhplus.be.server.queue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 대기열 토큰 발급 요청 DTO
 */
public class QueueTokenRequestDto {

    /** 사용자 고유 식별자 */
    @JsonProperty("userId")
    private String userId;

    /** 디바이스 핑거프린트 (선택적) */
    @JsonProperty("deviceFingerprint")
    private String deviceFingerprint;

    /**
     * 기본 생성자
     */
    public QueueTokenRequestDto() {}

    /**
     * 생성자
     *
     * @param userId 사용자 ID
     * @param deviceFingerprint 디바이스 핑거프린트
     */
    public QueueTokenRequestDto(String userId, String deviceFingerprint) {
        this.userId = userId;
        this.deviceFingerprint = deviceFingerprint;
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
     * 디바이스 핑거프린트를 반환합니다.
     *
     * @return 디바이스 핑거프린트
     */
    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    /**
     * 디바이스 핑거프린트를 설정합니다.
     *
     * @param deviceFingerprint 디바이스 핑거프린트
     */
    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    @Override
    public String toString() {
        return "QueueTokenRequestDto{" +
                "userId='" + userId + '\'' +
                ", deviceFingerprint='" + deviceFingerprint + '\'' +
                '}';
    }
}