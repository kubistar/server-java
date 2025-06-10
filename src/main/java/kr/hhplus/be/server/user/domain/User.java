package kr.hhplus.be.server.user.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    private String userId;

    @Column(nullable = false)
    private Long balance; // 원 단위

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 생성자
    public User(String userId, Long balance) {
        this.userId = userId;
        this.balance = balance;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 비즈니스 로직 메소드
    public void chargeBalance(Long amount) {
        validateChargeAmount(amount);
        this.balance += amount;
        this.updatedAt = LocalDateTime.now();
    }

    public void deductBalance(Long amount) {
        validateDeductAmount(amount);
        this.balance -= amount;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasEnoughBalance(Long amount) {
        return this.balance >= amount;
    }

    private void validateChargeAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
        }
        if (amount < 10000) {
            throw new IllegalArgumentException("최소 충전 금액은 10,000원입니다.");
        }
        if (amount > 1000000) {
            throw new IllegalArgumentException("최대 충전 금액은 1,000,000원입니다.");
        }
        if (amount % 1000 != 0) {
            throw new IllegalArgumentException("충전 금액은 1,000원 단위여야 합니다.");
        }
    }

    private void validateDeductAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("차감 금액은 0보다 커야 합니다.");
        }
        if (!hasEnoughBalance(amount)) {
            throw new IllegalArgumentException(
                    String.format("잔액이 부족합니다. 현재 잔액: %d원, 필요 금액: %d원", this.balance, amount)
            );
        }
    }
}