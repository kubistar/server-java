package kr.hhplus.be.server.payment.exception;

import lombok.Getter;

@Getter
public class InsufficientBalanceException extends RuntimeException {
    private final Long currentBalance;
    private final Long requiredAmount;
    private final Long shortfallAmount;

    public InsufficientBalanceException(Long currentBalance, Long requiredAmount) {
        super(String.format("잔액이 부족합니다. 현재 잔액: %d원, 필요 금액: %d원", currentBalance, requiredAmount));
        this.currentBalance = currentBalance;
        this.requiredAmount = requiredAmount;
        this.shortfallAmount = requiredAmount - currentBalance;
    }
}