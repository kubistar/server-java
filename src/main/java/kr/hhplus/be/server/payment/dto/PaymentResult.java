package kr.hhplus.be.server.payment.dto;

import kr.hhplus.be.server.payment.domain.Payment;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class PaymentResult {
    private final String paymentId;
    private final String reservationId;
    private final String userId;
    private final BigDecimal amount; // Long → BigDecimal 변경
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