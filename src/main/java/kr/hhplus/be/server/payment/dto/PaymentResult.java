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
        this.amount = payment.getAmount(); // BigDecimal 그대로 사용
        this.paymentMethod = payment.getPaymentMethod().name();
        this.status = payment.getStatus().name();
        this.paidAt = payment.getCreatedAt();
    }

    // 호환성을 위한 Long 반환 메서드 (필요시)
    public Long getAmountAsLong() {
        return amount != null ? amount.longValue() : 0L;
    }

    // 문자열 형태로 포맷된 금액 반환 (화면 표시용)
    public String getFormattedAmount() {
        return amount != null ? String.format("%,d원", amount.longValue()) : "0원";
    }
}