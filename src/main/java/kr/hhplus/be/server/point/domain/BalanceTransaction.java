package kr.hhplus.be.server.point.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "balance_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BalanceTransaction {

    @Id
    private String transactionId;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private Long balanceAfter;

    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public enum TransactionType {
        CHARGE, PAYMENT, REFUND
    }

    // 생성자
    public BalanceTransaction(String userId, TransactionType transactionType, Long amount, Long balanceAfter, String description) {
        this.transactionId = UUID.randomUUID().toString();
        this.userId = userId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    // 정적 팩토리 메소드
    public static BalanceTransaction charge(String userId, Long amount, Long balanceAfter) {
        return new BalanceTransaction(userId, TransactionType.CHARGE, amount, balanceAfter, "잔액 충전");
    }

    public static BalanceTransaction payment(String userId, Long amount, Long balanceAfter, String reservationId) {
        return new BalanceTransaction(userId, TransactionType.PAYMENT, amount, balanceAfter,
                "좌석 예약 결제 - 예약ID: " + reservationId);
    }

    public static BalanceTransaction refund(String userId, Long amount, Long balanceAfter, String reason) {
        return new BalanceTransaction(userId, TransactionType.REFUND, amount, balanceAfter, "환불 - " + reason);
    }
}