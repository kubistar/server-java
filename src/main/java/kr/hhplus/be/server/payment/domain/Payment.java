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

    /**
     * 결제 정보를 생성합니다.
     * 결제 ID는 자동으로 UUID로 생성되며, 상태는 PENDING으로 초기화됩니다.
     *
     * @param reservationId 예약 ID (필수, 36자 이하)
     * @param userId 사용자 ID (필수, 50자 이하)
     * @param amount 결제 금액 (필수, 0보다 큰 값)
     * @param paymentMethod 결제 방법 (필수)
     * @throws IllegalArgumentException 입력 데이터가 유효하지 않은 경우
     */
    public Payment(String reservationId, String userId, BigDecimal amount, PaymentMethod paymentMethod) {
        validatePaymentData(reservationId, userId, amount, paymentMethod);

        this.paymentId = UUID.randomUUID().toString();
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 실패 상태의 결제를 생성합니다.
     * 결제 처리 실패 시 기록용으로 사용됩니다.
     *
     * @param reservationId 예약 ID
     * @param userId 사용자 ID
     * @param amount 결제 금액
     * @param paymentMethod 결제 방법
     * @return FAILED 상태의 Payment 객체
     */
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

    /**
     * 결제를 완료 상태로 변경합니다.
     * PENDING 상태에서만 호출 가능합니다.
     *
     * @throws IllegalStateException PENDING 상태가 아닌 경우
     */
    public void markAsCompleted() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("대기 중인 결제만 완료 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.COMPLETED;
    }

    /**
     * 결제를 실패 상태로 변경합니다.
     * PENDING 상태에서만 호출 가능합니다.
     *
     * @throws IllegalStateException PENDING 상태가 아닌 경우
     */
    public void markAsFailed() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("대기 중인 결제만 실패 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.FAILED;
    }

    /**
     * 결제를 취소 상태로 변경합니다.
     * PENDING 상태에서만 호출 가능합니다.
     *
     * @throws IllegalStateException PENDING 상태가 아닌 경우
     */
    public void cancel() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("대기 중인 결제만 취소할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.CANCELLED;
    }

    /**
     * 결제를 환불 상태로 변경합니다.
     * COMPLETED 상태에서만 호출 가능합니다.
     *
     * @throws IllegalStateException COMPLETED 상태가 아닌 경우
     */
    public void refund() {
        if (this.status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("완료된 결제만 환불할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.REFUNDED;
    }

    /**
     * 실패한 결제를 재시도를 위해 PENDING 상태로 변경합니다.
     * FAILED 상태에서만 호출 가능합니다.
     *
     * @throws IllegalStateException FAILED 상태가 아닌 경우
     */
    public void retry() {
        if (this.status != PaymentStatus.FAILED) {
            throw new IllegalStateException("실패한 결제만 재시도할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.PENDING;
    }

    /**
     * 결제가 대기 상태인지 확인합니다.
     *
     * @return PENDING 상태이면 true, 아니면 false
     */
    public boolean isPending() {
        return this.status == PaymentStatus.PENDING;
    }

    /**
     * 결제가 완료 상태인지 확인합니다.
     *
     * @return COMPLETED 상태이면 true, 아니면 false
     */
    public boolean isCompleted() {
        return this.status == PaymentStatus.COMPLETED;
    }

    /**
     * 결제가 실패 상태인지 확인합니다.
     *
     * @return FAILED 상태이면 true, 아니면 false
     */
    public boolean isFailed() {
        return this.status == PaymentStatus.FAILED;
    }

    /**
     * 결제가 취소 상태인지 확인합니다.
     *
     * @return CANCELLED 상태이면 true, 아니면 false
     */
    public boolean isCancelled() {
        return this.status == PaymentStatus.CANCELLED;
    }

    /**
     * 결제가 환불 상태인지 확인합니다.
     *
     * @return REFUNDED 상태이면 true, 아니면 false
     */
    public boolean isRefunded() {
        return this.status == PaymentStatus.REFUNDED;
    }

    /**
     * 결제가 성공했는지 확인합니다.
     * isCompleted()와 동일한 기능입니다.
     *
     * @return COMPLETED 상태이면 true, 아니면 false
     */
    public boolean isSuccessful() {
        return this.status == PaymentStatus.COMPLETED;
    }

    /**
     * 결제가 최종 상태인지 확인합니다.
     * 최종 상태: COMPLETED, CANCELLED, REFUNDED
     *
     * @return 최종 상태이면 true, 아니면 false
     */
    public boolean isFinalState() {
        return this.status == PaymentStatus.COMPLETED ||
                this.status == PaymentStatus.CANCELLED ||
                this.status == PaymentStatus.REFUNDED;
    }

    /**
     * 결제 금액이 유효한지 확인합니다.
     *
     * @return 금액이 null이 아니고 0보다 크면 true, 아니면 false
     */
    public boolean isAmountValid() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 결제 금액이 지정된 금액과 같은지 확인합니다.
     *
     * @param compareAmount 비교할 금액
     * @return 금액이 같으면 true, 아니면 false
     */
    public boolean isAmountEqual(BigDecimal compareAmount) {
        return amount != null && amount.compareTo(compareAmount) == 0;
    }

    /**
     * 잔액 결제인지 확인합니다.
     *
     * @return 잔액 결제이면 true, 아니면 false
     */
    public boolean isBalancePayment() {
        return paymentMethod == PaymentMethod.BALANCE;
    }

    /**
     * 카드 결제인지 확인합니다.
     *
     * @return 카드 결제이면 true, 아니면 false
     */
    public boolean isCardPayment() {
        return paymentMethod == PaymentMethod.CARD;
    }

    /**
     * 결제 데이터의 유효성을 검증합니다.
     * 검증 조건:
     * - 예약 ID: 필수, 공백 불가
     * - 사용자 ID: 필수, 공백 불가
     * - 결제 금액: 필수, 0보다 큰 값
     * - 결제 방법: 필수
     *
     * @param reservationId 예약 ID
     * @param userId 사용자 ID
     * @param amount 결제 금액
     * @param paymentMethod 결제 방법
     * @throws IllegalArgumentException 유효하지 않은 데이터가 전달된 경우
     */
    private void validatePaymentData(String reservationId, String userId, BigDecimal amount, PaymentMethod paymentMethod) {

        if (reservationId == null || reservationId.trim().isEmpty()) {
            throw new IllegalArgumentException("예약 ID는 필수입니다.");
        }

        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }

        if (paymentMethod == null) {
            throw new IllegalArgumentException("결제 방법은 필수입니다.");
        }
    }
}