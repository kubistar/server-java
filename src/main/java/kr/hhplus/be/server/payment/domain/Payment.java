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
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum PaymentStatus {
        PENDING,      // 결제 대기
        COMPLETED,    // 결제 완료
        FAILED,       // 결제 실패
        CANCELLED,    // 결제 취소
        REFUNDED      // 환불 완료
    }

    public enum PaymentMethod {
        BALANCE, CARD
    }

    // 기본 생성자 - PENDING 상태로 시작
    public Payment(String reservationId, String userId, BigDecimal amount, PaymentMethod paymentMethod) {
        validatePaymentData(reservationId, userId, amount, paymentMethod);

        this.paymentId = UUID.randomUUID().toString();
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.PENDING; // 기본은 PENDING
        this.createdAt = LocalDateTime.now();
    }

    // 실패 상태로 결제 생성 (팩토리 메서드)
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

    // 상태 전이 메서드들
    public void markAsCompleted() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("대기 중인 결제만 완료 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.COMPLETED;
    }

    public void markAsFailed() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("대기 중인 결제만 실패 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.FAILED;
    }

    public void cancel() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("대기 중인 결제만 취소할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.CANCELLED;
    }

    public void refund() {
        if (this.status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("완료된 결제만 환불할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.REFUNDED;
    }

    public void retry() {
        if (this.status != PaymentStatus.FAILED) {
            throw new IllegalStateException("실패한 결제만 재시도할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.PENDING;
    }

    // 상태 확인 메서드들
    public boolean isPending() {
        return this.status == PaymentStatus.PENDING;
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

    public boolean isRefunded() {
        return this.status == PaymentStatus.REFUNDED;
    }

    public boolean isSuccessful() {
        return this.status == PaymentStatus.COMPLETED;
    }

    public boolean isFinalState() {
        return this.status == PaymentStatus.COMPLETED ||
                this.status == PaymentStatus.CANCELLED ||
                this.status == PaymentStatus.REFUNDED;
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

    public boolean isCardPayment() {
        return paymentMethod == PaymentMethod.CARD;
    }

    // 유효성 검증
    private void validatePaymentData(String reservationId, String userId, BigDecimal amount, PaymentMethod paymentMethod) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }

        if (paymentMethod == null) {
            throw new IllegalArgumentException("결제 방법은 필수입니다.");
        }
    }
}