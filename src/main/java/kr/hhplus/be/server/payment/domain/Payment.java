package kr.hhplus.be.server.payment.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    private String paymentId;

    @Column(nullable = false)
    private String reservationId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public enum PaymentStatus {
        COMPLETED, FAILED, CANCELLED
    }

    public enum PaymentMethod {
        BALANCE
    }

    // 생성자
    public Payment(String reservationId, String userId, Long amount, PaymentMethod paymentMethod) {
        this.paymentId = UUID.randomUUID().toString();
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.COMPLETED; // 생성 즉시 완료 상태
        this.createdAt = LocalDateTime.now();
    }

    // 테스트용 생성자
    public Payment(String paymentId, String reservationId, String userId, Long amount, PaymentMethod paymentMethod) {
        this.paymentId = paymentId;
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.COMPLETED;
        this.createdAt = LocalDateTime.now();
    }

    // 비즈니스 로직 메소드
    public void markAsFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public void markAsCancelled() {
        this.status = PaymentStatus.CANCELLED;
    }

    public boolean isCompleted() {
        return this.status == PaymentStatus.COMPLETED;
    }

    public boolean isFailed() {
        return this.status == PaymentStatus.FAILED;
    }
}
