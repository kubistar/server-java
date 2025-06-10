package kr.hhplus.be.server.seat;

import kr.hhplus.be.server.seat.domain.Seat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class SeatTest {

    @Test
    @DisplayName("좌석 생성 시 기본 상태는 AVAILABLE이어야 한다")
    void whenCreateSeat_ThenStatusShouldBeAvailable() {
        // given
        Long seatId = 1L;
        Long concertId = 1L;
        Integer seatNumber = 15;
        Integer price = 50000;

        // when
        Seat seat = new Seat(seatId, concertId, seatNumber, price);

        // then
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.AVAILABLE);
        assertThat(seat.isAvailable()).isTrue();
        assertThat(seat.isTemporarilyAssigned()).isFalse();
        assertThat(seat.isReserved()).isFalse();
    }

    @Test
    @DisplayName("예약 가능한 좌석에 임시 배정을 하면 상태가 TEMPORARILY_ASSIGNED로 변경된다")
    void whenAssignTemporarilyToAvailableSeat_ThenStatusShouldBeTemporarilyAssigned() {
        // given
        Seat seat = new Seat(1L, 1L, 15, 50000);
        String userId = "user-123";
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        // when
        seat.assignTemporarily(userId, expiresAt);

        // then
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.TEMPORARILY_ASSIGNED);
        assertThat(seat.getAssignedUserId()).isEqualTo(userId);
        assertThat(seat.getAssignedUntil()).isEqualTo(expiresAt);
        assertThat(seat.isTemporarilyAssigned()).isTrue();
        assertThat(seat.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("이미 배정된 좌석에 임시 배정을 시도하면 예외가 발생한다")
    void whenAssignTemporarilyToAssignedSeat_ThenShouldThrowException() {
        // given
        Seat seat = new Seat(1L, 1L, 15, 50000);
        seat.assignTemporarily("user-123", LocalDateTime.now().plusMinutes(5));

        // when & then
        assertThatThrownBy(() -> seat.assignTemporarily("user-456", LocalDateTime.now().plusMinutes(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("좌석이 이미 배정되었습니다.");
    }

    @Test
    @DisplayName("임시 배정된 좌석을 확정 예약하면 상태가 RESERVED로 변경된다")
    void whenConfirmReservationOnTemporarilyAssignedSeat_ThenStatusShouldBeReserved() {
        // given
        Seat seat = new Seat(1L, 1L, 15, 50000);
        seat.assignTemporarily("user-123", LocalDateTime.now().plusMinutes(5));
        LocalDateTime confirmedAt = LocalDateTime.now();

        // when
        seat.confirmReservation(confirmedAt);

        // then
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.RESERVED);
        assertThat(seat.getReservedAt()).isEqualTo(confirmedAt);
        assertThat(seat.isReserved()).isTrue();
    }

    @Test
    @DisplayName("임시 배정 시간이 만료된 좌석은 만료 상태로 인식된다")
    void whenTemporaryAssignmentExpired_ThenShouldBeExpired() {
        // given
        Seat seat = new Seat(1L, 1L, 15, 50000);
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1); // 1분 전
        seat.assignTemporarily("user-123", pastTime);

        // when & then
        assertThat(seat.isExpired()).isTrue();
    }

    @Test
    @DisplayName("임시 배정 해제하면 상태가 AVAILABLE로 변경된다")
    void whenReleaseAssignment_ThenStatusShouldBeAvailable() {
        // given
        Seat seat = new Seat(1L, 1L, 15, 50000);
        seat.assignTemporarily("user-123", LocalDateTime.now().plusMinutes(5));

        // when
        seat.releaseAssignment();

        // then
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.AVAILABLE);
        assertThat(seat.getAssignedUserId()).isNull();
        assertThat(seat.getAssignedUntil()).isNull();
        assertThat(seat.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("만료된 임시 배정을 확정 예약하려고 하면 예외가 발생한다")
    void whenConfirmExpiredTemporaryAssignment_ThenShouldThrowException() {
        // given
        Seat seat = new Seat(1L, 1L, 15, 50000);
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);
        seat.assignTemporarily("user-123", pastTime);

        // when & then
        assertThatThrownBy(() -> seat.confirmReservation(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("임시 배정 시간이 만료되었습니다.");
    }
}