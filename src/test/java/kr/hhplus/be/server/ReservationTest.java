package kr.hhplus.be.server;

import kr.hhplus.be.server.domain.Reservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class ReservationTest {

    @Test
    @DisplayName("예약 생성 시 기본 상태는 TEMPORARILY_ASSIGNED이어야 한다")
    void whenCreateReservation_ThenStatusShouldBeTemporarilyAssigned() {
        // given
        String userId = "user-123";
        Long concertId = 1L;
        Long seatId = 1L;
        Integer price = 50000;
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        // when
        Reservation reservation = new Reservation(userId, concertId, seatId, price, expiresAt);

        // then
        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.TEMPORARILY_ASSIGNED);
        assertThat(reservation.getUserId()).isEqualTo(userId);
        assertThat(reservation.getConcertId()).isEqualTo(concertId);
        assertThat(reservation.getSeatId()).isEqualTo(seatId);
        assertThat(reservation.getPrice()).isEqualTo(price);
        assertThat(reservation.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(reservation.getReservationId()).isNotNull();
    }

    @Test
    @DisplayName("임시 배정 상태의 예약을 확정하면 상태가 CONFIRMED로 변경된다")
    void whenConfirmTemporarilyAssignedReservation_ThenStatusShouldBeConfirmed() {
        // given
        Reservation reservation = new Reservation("user-123", 1L, 1L, 50000, LocalDateTime.now().plusMinutes(5));
        LocalDateTime confirmedAt = LocalDateTime.now();

        // when
        reservation.confirm(confirmedAt);

        // then
        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(confirmedAt);
    }

    @Test
    @DisplayName("만료된 예약을 확정하려고 하면 예외가 발생한다")
    void whenConfirmExpiredReservation_ThenShouldThrowException() {
        // given
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);
        Reservation reservation = new Reservation("user-123", 1L, 1L, 50000, pastTime);

        // when & then
        assertThatThrownBy(() -> reservation.confirm(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("예약이 만료되었습니다.");
    }

    @Test
    @DisplayName("임시 배정 상태의 예약을 취소하면 상태가 CANCELLED로 변경된다")
    void whenCancelTemporarilyAssignedReservation_ThenStatusShouldBeCancelled() {
        // given
        Reservation reservation = new Reservation("user-123", 1L, 1L, 50000, LocalDateTime.now().plusMinutes(5));

        // when
        reservation.cancel();

        // then
        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.CANCELLED);
    }

    @Test
    @DisplayName("남은 시간을 정확히 계산한다")
    void whenCalculateRemainingTime_ThenShouldReturnCorrectSeconds() {
        // given
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(300); // 5분 후
        Reservation reservation = new Reservation("user-123", 1L, 1L, 50000, expiresAt);

        // when
        long remainingTime = reservation.getRemainingTimeSeconds();

        // then
        assertThat(remainingTime).isBetween(295L, 300L); // 약 5분 (오차 허용)
    }

    @Test
    @DisplayName("만료된 예약의 남은 시간은 0이다")
    void whenReservationExpired_ThenRemainingTimeShouldBeZero() {
        // given
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);
        Reservation reservation = new Reservation("user-123", 1L, 1L, 50000, pastTime);

        // when
        long remainingTime = reservation.getRemainingTimeSeconds();

        // then
        assertThat(remainingTime).isZero();
        assertThat(reservation.isExpired()).isTrue();
    }
}