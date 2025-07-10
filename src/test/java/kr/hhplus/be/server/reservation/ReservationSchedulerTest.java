package kr.hhplus.be.server.reservation;

import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.repository.ReservationRepository;
import kr.hhplus.be.server.reservation.service.ReserveSeatUseCase;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatRepository;
import kr.hhplus.be.server.user.domain.User;
import kr.hhplus.be.server.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReservationSchedulerTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReserveSeatUseCase reserveSeatUseCase;

    @Test
    @DisplayName("🕒 예약 만료 후 스케줄러가 좌석 배정 해제 및 상태 변경을 수행해야 한다")
    @Transactional
    void reservationTimeoutScheduler_shouldReleaseSeatAndExpireReservation() {
        // given
        String userId = "timeout-user";
        userRepository.save(new User(userId));

        Seat seat = new Seat(1L, 999, BigDecimal.valueOf(30000));
        seat.assignTemporarily(userId, LocalDateTime.now().minusMinutes(10)); // 이미 만료됨
        seatRepository.save(seat);

        // 먼저 미래 시간으로 예약 생성 (validation 통과)
        Reservation reservation = new Reservation(
                userId,
                1L,
                seat.getSeatId(),
                seat.getPrice(),
                LocalDateTime.now().plusMinutes(10) // 미래 시간으로 생성
        );
        reservationRepository.save(reservation);

        // 예약을 저장한 후 만료 시간을 과거로 업데이트 (만료 상태 시뮬레이션)
        reservation.updateExpirationTime(LocalDateTime.now().minusMinutes(5));
        reservationRepository.save(reservation);

        // when
        reserveSeatUseCase.releaseExpiredReservations(); // 스케줄러 직접 호출

        // then
        Reservation updated = reservationRepository.findById(reservation.getReservationId()).orElseThrow();
        Seat updatedSeat = seatRepository.findById(seat.getSeatId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(Reservation.ReservationStatus.EXPIRED);
        assertThat(updatedSeat.isAvailable()).isTrue();
        assertThat(updatedSeat.getAssignedUserId()).isNull();
        assertThat(updatedSeat.isExpired()).isTrue();
    }
}