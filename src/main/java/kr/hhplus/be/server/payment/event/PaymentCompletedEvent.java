package kr.hhplus.be.server.payment.event;

import kr.hhplus.be.server.common.event.BaseEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class PaymentCompletedEvent extends BaseEvent {

    private String paymentId;
    private String reservationId;
    private String userId;
    private BigDecimal amount;
    private String paymentMethod;

    public PaymentCompletedEvent(String paymentId, String reservationId, String userId,
                                 BigDecimal amount, String paymentMethod) {
        super("PAYMENT_COMPLETED");
        this.paymentId = paymentId;
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
    }
}