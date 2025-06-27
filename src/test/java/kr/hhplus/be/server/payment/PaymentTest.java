package kr.hhplus.be.server.payment;

import kr.hhplus.be.server.payment.domain.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class PaymentTest {

    /*
    * PENDING → COMPLETED (정상 결제)
        PENDING → FAILED (결제 실패)
        PENDING → CANCELLED (사용자 취소)
        COMPLETED → REFUNDED (환불)
        FAILED → PENDING (재시도)
    * */

    @Test
    @DisplayName("결제 생성 시 기본 상태는 PENDING이다")
    void whenCreatePayment_ThenStatusShouldBePending() {
        // given
        String reservationId = "res-123";
        String userId = "user-123";
        BigDecimal amount = BigDecimal.valueOf(50000);

        // when
        Payment payment = new Payment(reservationId, userId, amount, Payment.PaymentMethod.BALANCE);

        // then
        assertThat(payment.getReservationId()).isEqualTo(reservationId);
        assertThat(payment.getUserId()).isEqualTo(userId);
        assertThat(payment.getAmount()).isEqualTo(amount);
        assertThat(payment.getPaymentMethod()).isEqualTo(Payment.PaymentMethod.BALANCE);
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
        assertThat(payment.getPaymentId()).isNotNull();
        assertThat(payment.isPending()).isTrue();
        assertThat(payment.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("PENDING 상태에서 결제 완료로 변경할 수 있다")
    void whenPendingPayment_ThenCanMarkAsCompleted() {
        // given
        Payment payment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);

        // when
        payment.markAsCompleted();

        // then
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(payment.isCompleted()).isTrue();
        assertThat(payment.isPending()).isFalse();
        assertThat(payment.isSuccessful()).isTrue();
    }

    @Test
    @DisplayName("PENDING 상태에서 결제 실패로 변경할 수 있다")
    void whenPendingPayment_ThenCanMarkAsFailed() {
        // given
        Payment payment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);

        // when
        payment.markAsFailed();

        // then
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(payment.isFailed()).isTrue();
        assertThat(payment.isPending()).isFalse();
        assertThat(payment.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("PENDING 상태에서 결제 취소할 수 있다")
    void whenPendingPayment_ThenCanCancel() {
        // given
        Payment payment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);

        // when
        payment.cancel();

        // then
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.CANCELLED);
        assertThat(payment.isCancelled()).isTrue();
        assertThat(payment.isPending()).isFalse();
    }

    @Test
    @DisplayName("완료된 결제는 환불할 수 있다")
    void whenCompletedPayment_ThenCanRefund() {
        // given
        Payment payment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);
        payment.markAsCompleted();

        // when
        payment.refund();

        // then
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
        assertThat(payment.isRefunded()).isTrue();
        assertThat(payment.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("실패한 결제는 재시도할 수 있다")
    void whenFailedPayment_ThenCanRetry() {
        // given
        Payment payment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);
        payment.markAsFailed();

        // when
        payment.retry();

        // then
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
        assertThat(payment.isPending()).isTrue();
        assertThat(payment.isFailed()).isFalse();
    }

    @Test
    @DisplayName("PENDING이 아닌 상태에서 완료 처리하면 예외가 발생한다")
    void whenNonPendingPayment_ThenCannotMarkAsCompleted() {
        // given
        Payment payment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);
        payment.markAsFailed(); // FAILED 상태로 변경

        // when & then
        assertThatThrownBy(() -> payment.markAsCompleted())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("대기 중인 결제만 완료 처리할 수 있습니다. 현재 상태: FAILED");
    }

    @Test
    @DisplayName("완료되지 않은 결제는 환불할 수 없다")
    void whenNonCompletedPayment_ThenCannotRefund() {
        // given
        Payment payment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);

        // when & then
        assertThatThrownBy(() -> payment.refund())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("완료된 결제만 환불할 수 있습니다. 현재 상태: PENDING");
    }

    @Test
    @DisplayName("실패하지 않은 결제는 재시도할 수 없다")
    void whenNonFailedPayment_ThenCannotRetry() {
        // given
        Payment payment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);

        // when & then
        assertThatThrownBy(() -> payment.retry())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("실패한 결제만 재시도할 수 있습니다. 현재 상태: PENDING");
    }

    @Test
    @DisplayName("createFailedPayment로 실패 상태 결제를 생성할 수 있다")
    void whenCreateFailedPayment_ThenStatusShouldBeFailed() {
        // given & when
        Payment payment = Payment.createFailedPayment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);

        // then
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(payment.isFailed()).isTrue();
        assertThat(payment.isPending()).isFalse();
    }

    @Test
    @DisplayName("금액이 유효한지 확인할 수 있다")
    void whenCheckAmountValid_ThenShouldReturnCorrectResult() {
        // given
        Payment validPayment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);

        // when & then
        assertThat(validPayment.isAmountValid()).isTrue();
    }

    @Test
    @DisplayName("0원 이하 금액으로 결제 생성 시 예외가 발생한다")
    void whenCreatePaymentWithInvalidAmount_ThenShouldThrowException() {
        // when & then
        assertThatThrownBy(() ->
                new Payment("res-123", "user-123", BigDecimal.ZERO, Payment.PaymentMethod.BALANCE)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("결제 금액은 0보다 커야 합니다.");

        assertThatThrownBy(() ->
                new Payment("res-123", "user-123", BigDecimal.valueOf(-1000), Payment.PaymentMethod.BALANCE)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("결제 금액은 0보다 커야 합니다.");
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
    @DisplayName("결제 방법을 정확히 확인할 수 있다")
    void whenCheckPaymentMethod_ThenShouldReturnCorrectResult() {
        // given
        Payment balancePayment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);

        // when & then
        assertThat(balancePayment.isBalancePayment()).isTrue();
        assertThat(balancePayment.isCardPayment()).isFalse();
    }

    @Test
    @DisplayName("최종 상태인지 확인할 수 있다")
    void whenCheckFinalState_ThenShouldReturnCorrectResult() {
        // given
        Payment pendingPayment = new Payment("res-1", "user-1",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);

        Payment completedPayment = new Payment("res-2", "user-2",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);
        completedPayment.markAsCompleted();

        Payment cancelledPayment = new Payment("res-3", "user-3",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);
        cancelledPayment.cancel();

        Payment refundedPayment = new Payment("res-4", "user-4",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);
        refundedPayment.markAsCompleted();
        refundedPayment.refund();

        // when & then
        assertThat(pendingPayment.isFinalState()).isFalse();
        assertThat(completedPayment.isFinalState()).isTrue();
        assertThat(cancelledPayment.isFinalState()).isTrue();
        assertThat(refundedPayment.isFinalState()).isTrue();
    }

    @Test
    @DisplayName("잘못된 파라미터로 결제 생성 시 예외가 발생한다")
    void whenCreatePaymentWithInvalidParams_ThenShouldThrowException() {
        // given
        BigDecimal validAmount = BigDecimal.valueOf(50000);

        // when & then
        assertThatThrownBy(() ->
                new Payment(null, "user-123", validAmount, Payment.PaymentMethod.BALANCE)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("예약 ID는 필수입니다.");

        assertThatThrownBy(() ->
                new Payment("res-123", null, validAmount, Payment.PaymentMethod.BALANCE)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자 ID는 필수입니다.");

        assertThatThrownBy(() ->
                new Payment("res-123", "user-123", validAmount, null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("결제 방법은 필수입니다.");
    }

    @Test
    @DisplayName("상태 전이 시나리오 테스트 - 정상 결제 플로우")
    void scenarioNormalPaymentFlow() {
        // given
        Payment payment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);

        // 1. 초기 상태: PENDING
        assertThat(payment.isPending()).isTrue();

        // 2. 결제 완료
        payment.markAsCompleted();
        assertThat(payment.isCompleted()).isTrue();

        // 3. 환불
        payment.refund();
        assertThat(payment.isRefunded()).isTrue();
        assertThat(payment.isFinalState()).isTrue();
    }

    @Test
    @DisplayName("상태 전이 시나리오 테스트 - 실패 후 재시도")
    void scenarioFailedPaymentRetry() {
        // given
        Payment payment = new Payment("res-123", "user-123",
                BigDecimal.valueOf(50000), Payment.PaymentMethod.BALANCE);

        // 1. 초기 상태: PENDING
        assertThat(payment.isPending()).isTrue();

        // 2. 결제 실패
        payment.markAsFailed();
        assertThat(payment.isFailed()).isTrue();

        // 3. 재시도
        payment.retry();
        assertThat(payment.isPending()).isTrue();

        // 4. 재시도 후 성공
        payment.markAsCompleted();
        assertThat(payment.isCompleted()).isTrue();
    }
}