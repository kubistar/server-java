package kr.hhplus.be.server.balance.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "balance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Balance {

    @Id
    private String userId;

    @Column(nullable = false)
    private BigDecimal amount; // Long → BigDecimal로 변경

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 생성자
    public Balance(String userId, BigDecimal amount) {
        this.userId = userId;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Long을 받는 편의 생성자 (기존 코드 호환)
    public Balance(String userId, Long amount) {
        this(userId, BigDecimal.valueOf(amount));
    }

    // 비즈니스 로직 메소드
    public void chargeAmount(BigDecimal chargeAmount) {
        validateChargeAmount(chargeAmount);
        this.amount = this.amount.add(chargeAmount);
        this.updatedAt = LocalDateTime.now();
    }

    // Long을 받는 편의 메서드 (기존 코드 호환)
    public void chargeAmount(Long chargeAmount) {
        chargeAmount(BigDecimal.valueOf(chargeAmount));
    }

    public void deductAmount(BigDecimal deductAmount) {
        validateDeductAmount(deductAmount);
        this.amount = this.amount.subtract(deductAmount);
        this.updatedAt = LocalDateTime.now();
    }

    public void deductAmount(Long deductAmount) {
        deductAmount(BigDecimal.valueOf(deductAmount));
    }

    public boolean hasEnoughAmount(BigDecimal requiredAmount) {
        return this.amount.compareTo(requiredAmount) >= 0;
    }

    public boolean hasEnoughAmount(Long requiredAmount) {
        return hasEnoughAmount(BigDecimal.valueOf(requiredAmount));
    }

    private void validateChargeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
        }
        if (amount.compareTo(BigDecimal.valueOf(10000)) < 0) {
            throw new IllegalArgumentException("최소 충전 금액은 10,000원입니다.");
        }
        if (amount.compareTo(BigDecimal.valueOf(1000000)) > 0) {
            throw new IllegalArgumentException("최대 충전 금액은 1,000,000원입니다.");
        }
        // 1000원 단위 체크
        if (amount.remainder(BigDecimal.valueOf(1000)).compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("충전 금액은 1,000원 단위여야 합니다.");
        }
    }

    private void validateDeductAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("차감 금액은 0보다 커야 합니다.");
        }
        if (!hasEnoughAmount(amount)) {
            throw new IllegalArgumentException(
                    String.format("잔액이 부족합니다. 현재 잔액: %s원, 필요 금액: %s원", this.amount, amount)
            );
        }
    }
}