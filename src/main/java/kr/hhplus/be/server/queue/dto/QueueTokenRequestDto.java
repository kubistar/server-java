package kr.hhplus.be.server.queue.dto;

/**
 * 대기열 토큰 발급 요청 DTO
 */
public class QueueTokenRequestDto {
    private String userId;

    public QueueTokenRequestDto() {}

    public QueueTokenRequestDto(String userId) {
        this.userId = userId;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}

