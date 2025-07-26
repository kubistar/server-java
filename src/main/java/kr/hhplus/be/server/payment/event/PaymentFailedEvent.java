package kr.hhplus.be.server.payment.event;

import kr.hhplus.be.server.common.event.BaseEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class PaymentFailedEvent extends BaseEvent {

    private String paymentId;
    private String reservationId;
    private String userId;
    private BigDecimal amount;
    private String failureReason;

    public PaymentFailedEvent(String paymentId, String reservationId, String userId,
                              BigDecimal amount, String failureReason) {
        super("PAYMENT_FAILED");
        this.paymentId = paymentId;
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.failureReason = failureReason;
    }
}