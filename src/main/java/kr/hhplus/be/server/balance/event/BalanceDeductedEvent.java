package kr.hhplus.be.server.balance.event;

import kr.hhplus.be.server.common.event.BaseEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class BalanceDeductedEvent extends BaseEvent {

    private String userId;
    private BigDecimal deductedAmount;
    private BigDecimal balanceAfter;
    private String reservationId;
    private String transactionId;

    public BalanceDeductedEvent(String userId, BigDecimal deductedAmount, BigDecimal balanceAfter,
                                String reservationId, String transactionId) {
        super("BALANCE_DEDUCTED");
        this.userId = userId;
        this.deductedAmount = deductedAmount;
        this.balanceAfter = balanceAfter;
        this.reservationId = reservationId;
        this.transactionId = transactionId;
    }
}