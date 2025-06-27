package kr.hhplus.be.server.payment;

import kr.hhplus.be.server.payment.domain.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class PaymentTest {

    @Test
    @DisplayName("결제 생성 시 기본 상태는 COMPLETED이다")
    void whenCreatePayment_ThenStatusShouldBeCompleted() {
        // given
        String reservationId = "res-123";
        String userId = "user-123";
        BigDecimal amount = BigDecimal.valueOf(50000); // Long → BigDecimal 변경

        // when
        Payment payment = new Payment(reservationId, userId, amount, Payment.PaymentMethod.BALANCE);

        // then
        assertThat(payment.getReservationId()).isEqualTo(reservationId);
        assertThat(payment.getUserId()).isEqualTo(userId);
        assertThat(payment.getAmount()).isEqualTo(amount);
        assertThat(payment.getPaymentMethod()).isEqualTo(Payment.PaymentMethod.BALANCE);
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(payment.getPaymentId()).isNotNull();
        assertThat(payment.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("결제를 실패 상태로 변경할 수 있다")
    void whenMarkAsFailed_ThenStatusShouldBeFailed() {
        // given
        Payment payment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE); // Long → BigDecimal 변경

        // when
        payment.markAsFailed();

        // then
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(payment.isFailed()).isTrue();
        assertThat(payment.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("결제를 취소 상태로 변경할 수 있다")
    void whenMarkAsCancelled_ThenStatusShouldBeCancelled() {
        // given
        Payment payment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE); // Long → BigDecimal 변경

        // when
        payment.markAsCancelled();

        // then
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.CANCELLED);
        assertThat(payment.isCompleted()).isFalse();
        assertThat(payment.isFailed()).isFalse();
    }

    @Test
    @DisplayName("완료된 결제는 실패로 변경할 수 없다")
    void whenMarkCompletedPaymentAsFailed_ThenShouldThrowException() {
        // given
        Payment payment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);

        // 이미 COMPLETED 상태

        // when & then
        assertThatThrownBy(() -> payment.markAsFailed())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("완료된 결제는 실패 처리할 수 없습니다.");
    }

    @Test
    @DisplayName("실패한 결제는 취소할 수 없다")
    void whenMarkFailedPaymentAsCancelled_ThenShouldThrowException() {
        // given
        Payment payment = Payment.createFailedPayment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);

        // when & then
        assertThatThrownBy(() -> payment.markAsCancelled())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("실패한 결제는 취소할 수 없습니다.");
    }

    @Test
    @DisplayName("금액이 유효한지 확인할 수 있다")
    void whenCheckAmountValid_ThenShouldReturnCorrectResult() {
        // given
        Payment validPayment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);

        Payment invalidPayment = new Payment("res-456", "user-456",
                BigDecimal.ZERO, Payment.PaymentMethod.BALANCE);

        // when & then
        assertThat(validPayment.isAmountValid()).isTrue();
        assertThat(invalidPayment.isAmountValid()).isFalse();
    }

    @Test
    @DisplayName("금액 비교가 정확히 동작한다")
    void whenCompareAmount_ThenShouldReturnCorrectResult() {
        // given
        BigDecimal amount = BigDecimal.valueOf(50000);
        Payment payment = new Payment("res-123", "user-123", amount, Payment.PaymentMethod.BALANCE);

        // when & then
        assertThat(payment.isAmountEqual(BigDecimal.valueOf(50000))).isTrue();
        assertThat(payment.isAmountEqual(BigDecimal.valueOf(60000))).isFalse();
    }

    @Test
    @DisplayName("잔액 결제인지 확인할 수 있다")
    void whenCheckBalancePayment_ThenShouldReturnTrue() {
        // given
        Payment payment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);

        // when & then
        assertThat(payment.isBalancePayment()).isTrue();
    }
}