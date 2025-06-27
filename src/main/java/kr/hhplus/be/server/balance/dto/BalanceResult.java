package kr.hhplus.be.server.balance.dto;

import kr.hhplus.be.server.balance.domain.Balance;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class BalanceResult {
    private final String userId;
    private final BigDecimal currentBalance;  // Long → BigDecimal 변경
    private final LocalDateTime lastUpdatedAt;

    // Balance 엔티티를 받는 생성자
    public BalanceResult(Balance balance) {
        this.userId = balance.getUserId();
        this.currentBalance = balance.getAmount();  // getBalance() → getAmount()
        this.lastUpdatedAt = balance.getUpdatedAt();
    }
}