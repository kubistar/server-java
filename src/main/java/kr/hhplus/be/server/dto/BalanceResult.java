package kr.hhplus.be.server.dto;

import kr.hhplus.be.server.domain.User;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class BalanceResult {
    private final String userId;
    private final Long currentBalance;
    private final LocalDateTime lastUpdatedAt;

    public BalanceResult(User user) {
        this.userId = user.getUserId();
        this.currentBalance = user.getBalance();
        this.lastUpdatedAt = user.getUpdatedAt();
    }
}