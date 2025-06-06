package kr.hhplus.be.server;

import kr.hhplus.be.server.domain.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaymentTest {

    @Test
    @DisplayName("결제 생성 시 기본 상태는 COMPLETED이다")
    void whenCreatePayment_ThenStatusShouldBeCompleted() {
        // given
        String reservationId = "res-123";
        String userId = "user-123";
        Long amount = 50000L;

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
        Payment payment = new Payment("res-123", "user-123", 50000L, Payment.PaymentMethod.BALANCE);

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
        Payment payment = new Payment("res-123", "user-123", 50000L, Payment.PaymentMethod.BALANCE);

        // when
        payment.markAsCancelled();

        // then
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.CANCELLED);
        assertThat(payment.isCompleted()).isFalse();
        assertThat(payment.isFailed()).isFalse();
    }
}
