package kr.hhplus.be.server.exception;

import lombok.Getter;

@Getter
public class PaymentNotFoundException extends RuntimeException {
    private final String paymentId;

    public PaymentNotFoundException(String paymentId) {
        super(String.format("결제 정보를 찾을 수 없습니다: %s", paymentId));
        this.paymentId = paymentId;
    }
}