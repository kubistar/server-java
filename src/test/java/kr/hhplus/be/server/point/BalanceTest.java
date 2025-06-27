package kr.hhplus.be.server.point;

import kr.hhplus.be.server.balance.domain.Balance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class BalanceTest {

    @Test
    @DisplayName("잔액 생성 시 초기 금액이 설정된다")
    void whenCreateBalance_ThenAmountShouldBeSet() {
        // given
        String userId = "user-123";
        BigDecimal initialAmount = BigDecimal.valueOf(100000);

        // when
        Balance balance = new Balance(userId, initialAmount);

        // then
        assertThat(balance.getUserId()).isEqualTo(userId);
        assertThat(balance.getAmount()).isEqualTo(initialAmount);
        assertThat(balance.getCreatedAt()).isNotNull();
        assertThat(balance.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("유효한 금액으로 잔액 충전이 성공한다")
    void whenChargeValidAmount_ThenAmountShouldIncrease() {
        // given
        Balance balance = new Balance("user-123", BigDecimal.valueOf(50000));
        BigDecimal chargeAmount = BigDecimal.valueOf(100000);

        // when
        balance.chargeAmount(chargeAmount);

        // then
        assertThat(balance.getAmount()).isEqualTo(BigDecimal.valueOf(150000));
    }

    @Test
    @DisplayName("최소 충전 금액 미만으로 충전 시 예외가 발생한다")
    void whenChargeAmountBelowMinimum_ThenShouldThrowException() {
        // given
        Balance balance = new Balance("user-123", BigDecimal.valueOf(50000));
        BigDecimal invalidAmount = BigDecimal.valueOf(5000); // 최소 10,000원 미만

        // when & then
        assertThatThrownBy(() -> balance.chargeAmount(invalidAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("최소 충전 금액은 10,000원입니다.");
    }

    @Test
    @DisplayName("충분한 잔액이 있을 때 차감이 성공한다")
    void whenDeductWithSufficientAmount_ThenShouldSucceed() {
        // given
        Balance balance = new Balance("user-123", BigDecimal.valueOf(100000));
        BigDecimal deductAmount = BigDecimal.valueOf(30000);

        // when
        balance.deductAmount(deductAmount);

        // then
        assertThat(balance.getAmount()).isEqualTo(BigDecimal.valueOf(70000));
    }

    @Test
    @DisplayName("잔액 부족 시 차감이 실패한다")
    void whenDeductWithInsufficientAmount_ThenShouldThrowException() {
        // given
        Balance balance = new Balance("user-123", BigDecimal.valueOf(30000));
        BigDecimal deductAmount = BigDecimal.valueOf(50000);

        // when & then
        assertThatThrownBy(() -> balance.deductAmount(deductAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("잔액이 부족합니다");
    }

    @Test
    @DisplayName("잔액 충분 여부를 정확히 판단한다")
    void whenCheckAmount_ThenShouldReturnCorrectResult() {
        // given
        Balance balance = new Balance("user-123", BigDecimal.valueOf(50000));

        // when & then
        assertThat(balance.hasEnoughAmount(BigDecimal.valueOf(30000))).isTrue();
        assertThat(balance.hasEnoughAmount(BigDecimal.valueOf(50000))).isTrue();
        assertThat(balance.hasEnoughAmount(BigDecimal.valueOf(60000))).isFalse();
    }

    @Test
    @DisplayName("Long 타입으로도 정상 동작한다 (하위 호환)")
    void whenUseLongType_ThenShouldWorkProperly() {
        // given
        Balance balance = new Balance("user-123", 50000L);

        // when
        balance.chargeAmount(30000L);

        // then
        assertThat(balance.getAmount()).isEqualTo(BigDecimal.valueOf(80000));
        assertThat(balance.hasEnoughAmount(70000L)).isTrue();
    }

    @Test
    @DisplayName("1000원 단위가 아닌 금액으로 충전 시 예외가 발생한다")
    void whenChargeNonThousandUnit_ThenShouldThrowException() {
        // given
        Balance balance = new Balance("user-123", BigDecimal.valueOf(50000));
        BigDecimal invalidAmount = BigDecimal.valueOf(10500); // 1000원 단위가 아님

        // when & then
        assertThatThrownBy(() -> balance.chargeAmount(invalidAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("충전 금액은 1,000원 단위여야 합니다.");
    }

    @Test
    @DisplayName("최대 충전 금액을 초과하면 예외가 발생한다")
    void whenChargeOverMaximum_ThenShouldThrowException() {
        // given
        Balance balance = new Balance("user-123", BigDecimal.valueOf(50000));
        BigDecimal invalidAmount = BigDecimal.valueOf(1100000); // 최대 1,000,000원 초과

        // when & then
        assertThatThrownBy(() -> balance.chargeAmount(invalidAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("최대 충전 금액은 1,000,000원입니다.");
    }
}