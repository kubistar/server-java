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

    /**
     * 충전 금액을 설정
     * Long 타입의 금액을 BigDecimal로 변환하여 충전
     *
     * @param chargeAmount 충전할 금액 (Long 타입)
     */
    public void chargeAmount(Long chargeAmount) {
        chargeAmount(BigDecimal.valueOf(chargeAmount));
    }

    /**
     * 계정에서 지정된 금액을 차감
     * 차감 전 유효성 검증을 수행하며, 차감 후 업데이트 시간을 현재 시간으로 설정
     *
     * @param deductAmount 차감할 금액 (BigDecimal 타입)
     * @throws IllegalArgumentException 차감 금액이 null이거나 0 이하인 경우, 또는 잔액이 부족한 경우
     */
    public void deductAmount(BigDecimal deductAmount) {
        validateDeductAmount(deductAmount);
        this.amount = this.amount.subtract(deductAmount);
        this.updatedAt = LocalDateTime.now();
    }


    /**
     * 현재 잔액이 요청된 금액 이상인지 확인
     *
     * @param requiredAmount 확인할 필요 금액 (BigDecimal 타입)
     * @return 잔액이 충분하면 true, 부족하면 false
     */
    public boolean hasEnoughAmount(BigDecimal requiredAmount) {
        return this.amount.compareTo(requiredAmount) >= 0;
    }


    /**
     * 현재 잔액이 요청된 금액 이상인지 확인
     * Long 타입의 금액을 BigDecimal로 변환하여 확인
     *
     * @param requiredAmount 확인할 필요 금액 (Long 타입)
     * @return 잔액이 충분하면 true, 부족하면 false
     */
    public boolean hasEnoughAmount(Long requiredAmount) {
        return hasEnoughAmount(BigDecimal.valueOf(requiredAmount));
    }


    /**
     * 충전 금액의 유효성을 검증
     * 검증 조건:
     * - null이 아니고 0보다 큰 값
     * - 최소 충전 금액: 10,000원
     * - 최대 충전 금액: 1,000,000원
     * - 1,000원 단위여야 함
     *
     * @param amount 검증할 충전 금액
     * @throws IllegalArgumentException 충전 금액이 null이거나 0 이하인 경우,
     *                                  최소/최대 충전 금액을 벗어나는 경우,
     *                                  1,000원 단위가 아닌 경우
     */
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


    /**
     * 차감 금액의 유효성을 검증
     * 검증 조건:
     * - null이 아니고 0보다 큰 값
     * - 현재 잔액 이하여야 함
     *
     * @param amount 검증할 차감 금액
     * @throws IllegalArgumentException 차감 금액이 null이거나 0 이하인 경우,
     *                                  또는 잔액이 부족한 경우
     */
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