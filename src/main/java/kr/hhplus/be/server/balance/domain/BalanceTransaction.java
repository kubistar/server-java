package kr.hhplus.be.server.balance.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "balance_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BalanceTransaction {

    @Id
    @Column(name = "transaction_id", length = 36)
    private String transactionId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount; // Long → BigDecimal로 변경

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter; // Long → BigDecimal로 변경

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum TransactionType {
        CHARGE, PAYMENT, REFUND
    }

    // 생성자
    public BalanceTransaction(String userId, TransactionType transactionType, BigDecimal amount,
                              BigDecimal balanceAfter, String description) {
        validateTransactionData(userId, transactionType, amount, balanceAfter, description);

        this.transactionId = UUID.randomUUID().toString();
        this.userId = userId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    // 정적 팩토리 메소드
    public static BalanceTransaction charge(String userId, BigDecimal amount, BigDecimal balanceAfter) {
        return new BalanceTransaction(userId, TransactionType.CHARGE, amount, balanceAfter, "잔액 충전");
    }

    public static BalanceTransaction payment(String userId, BigDecimal amount, BigDecimal balanceAfter, String reservationId) {
        String description = "좌석 예약 결제 - 예약ID: " + reservationId;
        return new BalanceTransaction(userId, TransactionType.PAYMENT, amount, balanceAfter, description);
    }

    public static BalanceTransaction refund(String userId, BigDecimal amount, BigDecimal balanceAfter, String reason) {
        String description = "환불 - " + reason;
        return new BalanceTransaction(userId, TransactionType.REFUND, amount, balanceAfter, description);
    }

    // 편의 정적 팩토리 메소드 (상세 설명 포함)
    public static BalanceTransaction chargeWithDetail(String userId, BigDecimal amount, BigDecimal balanceAfter, String detail) {
        String description = "잔액 충전 - " + detail;
        return new BalanceTransaction(userId, TransactionType.CHARGE, amount, balanceAfter, description);
    }

    public static BalanceTransaction paymentWithDetail(String userId, BigDecimal amount, BigDecimal balanceAfter,
                                                       String reservationId, String concertTitle) {
        String description = String.format("좌석 예약 결제 - 예약ID: %s, 콘서트: %s", reservationId, concertTitle);
        return new BalanceTransaction(userId, TransactionType.PAYMENT, amount, balanceAfter, description);
    }

    // 비즈니스 로직 메소드
    public boolean isChargeTransaction() {
        return transactionType == TransactionType.CHARGE;
    }

    public boolean isPaymentTransaction() {
        return transactionType == TransactionType.PAYMENT;
    }

    public boolean isRefundTransaction() {
        return transactionType == TransactionType.REFUND;
    }

    public boolean isDebitTransaction() {
        return transactionType == TransactionType.PAYMENT;
    }

    public boolean isCreditTransaction() {
        return transactionType == TransactionType.CHARGE || transactionType == TransactionType.REFUND;
    }

    // 금액 관련 메서드
    public boolean isAmountValid() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isBalanceAfterValid() {
        return balanceAfter != null && balanceAfter.compareTo(BigDecimal.ZERO) >= 0;
    }

    public BigDecimal getBalanceBeforeTransaction() {
        if (isDebitTransaction()) {
            return balanceAfter.add(amount);
        } else {
            return balanceAfter.subtract(amount);
        }
    }

    // 설명 관련 메서드
    public boolean hasDescription() {
        return description != null && !description.trim().isEmpty();
    }

    public String getShortDescription() {
        if (description == null) return "";
        return description.length() > 50 ? description.substring(0, 47) + "..." : description;
    }

    // 유효성 검증
    private void validateTransactionData(String userId, TransactionType transactionType,
                                         BigDecimal amount, BigDecimal balanceAfter, String description) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (userId.length() > 50) {
            throw new IllegalArgumentException("사용자 ID는 50자를 초과할 수 없습니다.");
        }

        if (transactionType == null) {
            throw new IllegalArgumentException("거래 유형은 필수입니다.");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("거래 금액은 0보다 커야 합니다.");
        }

        if (balanceAfter == null || balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("거래 후 잔액은 0 이상이어야 합니다.");
        }

        if (description != null && description.length() > 500) {
            throw new IllegalArgumentException("설명은 500자를 초과할 수 없습니다.");
        }
    }
}