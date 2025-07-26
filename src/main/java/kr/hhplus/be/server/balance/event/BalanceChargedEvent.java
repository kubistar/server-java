package kr.hhplus.be.server.balance.event;

import kr.hhplus.be.server.common.event.BaseEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class BalanceChargedEvent extends BaseEvent {

    private String userId;
    private BigDecimal chargedAmount;
    private BigDecimal balanceAfter;
    private String transactionId;

    public BalanceChargedEvent(String userId, BigDecimal chargedAmount, BigDecimal balanceAfter, String transactionId) {
        super("BALANCE_CHARGED");
        this.userId = userId;
        this.chargedAmount = chargedAmount;
        this.balanceAfter = balanceAfter;
        this.transactionId = transactionId;
    }
}