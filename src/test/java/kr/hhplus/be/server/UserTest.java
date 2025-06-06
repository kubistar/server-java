package kr.hhplus.be.server;

import kr.hhplus.be.server.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UserTest {

    @Test
    @DisplayName("사용자 생성 시 초기 잔액이 설정된다")
    void whenCreateUser_ThenBalanceShouldBeSet() {
        // given
        String userId = "user-123";
        Long initialBalance = 100000L;

        // when
        User user = new User(userId, initialBalance);

        // then
        assertThat(user.getUserId()).isEqualTo(userId);
        assertThat(user.getBalance()).isEqualTo(initialBalance);
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("유효한 금액으로 잔액 충전이 성공한다")
    void whenChargeValidAmount_ThenBalanceShouldIncrease() {
        // given
        User user = new User("user-123", 50000L);
        Long chargeAmount = 100000L;

        // when
        user.chargeBalance(chargeAmount);

        // then
        assertThat(user.getBalance()).isEqualTo(150000L);
    }

    @Test
    @DisplayName("최소 충전 금액 미만으로 충전 시 예외가 발생한다")
    void whenChargeAmountBelowMinimum_ThenShouldThrowException() {
        // given
        User user = new User("user-123", 50000L);
        Long invalidAmount = 5000L; // 최소 10,000원 미만

        // when & then
        assertThatThrownBy(() -> user.chargeBalance(invalidAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("최소 충전 금액은 10,000원입니다.");
    }

    @Test
    @DisplayName("충분한 잔액이 있을 때 차감이 성공한다")
    void whenDeductWithSufficientBalance_ThenShouldSucceed() {
        // given
        User user = new User("user-123", 100000L);
        Long deductAmount = 30000L;

        // when
        user.deductBalance(deductAmount);

        // then
        assertThat(user.getBalance()).isEqualTo(70000L);
    }

    @Test
    @DisplayName("잔액 부족 시 차감이 실패한다")
    void whenDeductWithInsufficientBalance_ThenShouldThrowException() {
        // given
        User user = new User("user-123", 30000L);
        Long deductAmount = 50000L;

        // when & then
        assertThatThrownBy(() -> user.deductBalance(deductAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("잔액이 부족합니다");
    }

    @Test
    @DisplayName("잔액 충분 여부를 정확히 판단한다")
    void whenCheckBalance_ThenShouldReturnCorrectResult() {
        // given
        User user = new User("user-123", 50000L);

        // when & then
        assertThat(user.hasEnoughBalance(30000L)).isTrue();
        assertThat(user.hasEnoughBalance(50000L)).isTrue();
        assertThat(user.hasEnoughBalance(60000L)).isFalse();
    }
}