package kr.hhplus.be.server.dto;

import kr.hhplus.be.server.domain.Payment;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class PaymentResult {
    private final String paymentId;
    private final String reservationId;
    private final String userId;
    private final Long amount;
    private final String paymentMethod;
    private final String status;
    private final LocalDateTime paidAt;

    public PaymentResult(Payment payment) {
        this.paymentId = payment.getPaymentId();
        this.reservationId = payment.getReservationId();
        this.userId = payment.getUserId();
        this.amount = payment.getAmount();
        this.paymentMethod = payment.getPaymentMethod().name();
        this.status = payment.getStatus().name();
        this.paidAt = payment.getCreatedAt();
    }
}