package kr.hhplus.be.server.payment.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @Column(name = "payment_id", length = 36)
    private String paymentId;

    @Column(name = "reservation_id", nullable = false, length = 36)
    private String reservationId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount; // Long → BigDecimal로 변경

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum PaymentStatus {
        COMPLETED, FAILED, CANCELLED
    }

    public enum PaymentMethod {
        BALANCE
    }

    // 생성자
    public Payment(String reservationId, String userId, BigDecimal amount, PaymentMethod paymentMethod) {
        validatePaymentData(reservationId, userId, amount, paymentMethod);

        this.paymentId = UUID.randomUUID().toString();
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.COMPLETED; // 생성 즉시 완료 상태
        this.createdAt = LocalDateTime.now();
    }

    // 테스트용 생성자
    public Payment(String paymentId, String reservationId, String userId, BigDecimal amount, PaymentMethod paymentMethod) {
        validatePaymentData(reservationId, userId, amount, paymentMethod);

        this.paymentId = paymentId;
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.COMPLETED;
        this.createdAt = LocalDateTime.now();
    }

    // 실패 상태로 결제 생성 (실패 케이스용)
    public static Payment createFailedPayment(String reservationId, String userId, BigDecimal amount, PaymentMethod paymentMethod) {
        Payment payment = new Payment();
        payment.paymentId = UUID.randomUUID().toString();
        payment.reservationId = reservationId;
        payment.userId = userId;
        payment.amount = amount;
        payment.paymentMethod = paymentMethod;
        payment.status = PaymentStatus.FAILED;
        payment.createdAt = LocalDateTime.now();
        return payment;
    }

    // 비즈니스 로직 메소드
    public void markAsFailed() {
        if (this.status == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("완료된 결제는 실패 처리할 수 없습니다.");
        }
        this.status = PaymentStatus.FAILED;
    }

    public void markAsCancelled() {
        if (this.status == PaymentStatus.FAILED) {
            throw new IllegalStateException("실패한 결제는 취소할 수 없습니다.");
        }
        this.status = PaymentStatus.CANCELLED;
    }

    public boolean isCompleted() {
        return this.status == PaymentStatus.COMPLETED;
    }

    public boolean isFailed() {
        return this.status == PaymentStatus.FAILED;
    }

    public boolean isCancelled() {
        return this.status == PaymentStatus.CANCELLED;
    }

    public boolean isSuccessful() {
        return this.status == PaymentStatus.COMPLETED;
    }

    // 금액 관련 메서드
    public boolean isAmountValid() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isAmountEqual(BigDecimal compareAmount) {
        return amount != null && amount.compareTo(compareAmount) == 0;
    }

    // 결제 방법 확인
    public boolean isBalancePayment() {
        return paymentMethod == PaymentMethod.BALANCE;
    }

    // 유효성 검증
    private void validatePaymentData(String reservationId, String userId, BigDecimal amount, PaymentMethod paymentMethod) {
        if (reservationId == null || reservationId.trim().isEmpty()) {
            throw new IllegalArgumentException("예약 ID는 필수입니다.");
        }
        if (reservationId.length() > 36) {
            throw new IllegalArgumentException("예약 ID는 36자를 초과할 수 없습니다.");
        }

        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (userId.length() > 50) {
            throw new IllegalArgumentException("사용자 ID는 50자를 초과할 수 없습니다.");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }

        if (paymentMethod == null) {
            throw new IllegalArgumentException("결제 방법은 필수입니다.");
        }
    }
}