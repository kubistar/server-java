package kr.hhplus.be.server.reservation;

import kr.hhplus.be.server.reservation.command.ReserveSeatCommand;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.reservation.dto.ReservationResult;
import kr.hhplus.be.server.seat.exception.SeatNotAvailableException;
import kr.hhplus.be.server.reservation.repository.ReservationJpaRepository;
import kr.hhplus.be.server.seat.repository.SeatJpaRepository;
import kr.hhplus.be.server.reservation.service.ReserveSeatUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReservationIntegrationTest {

    @Autowired
    private ReserveSeatUseCase reserveSeatUseCase;

    @Autowired
    private SeatJpaRepository seatJpaRepository;

    @Autowired
    private ReservationJpaRepository reservationJpaRepository;

    private Seat testSeat;
    private Long concertId = 1L;
    private Integer seatNumber = 15;

    @BeforeEach
    void setUp() {
        // 테스트용 좌석 생성
        testSeat = new Seat(null, concertId, seatNumber, 50000);
        testSeat = seatJpaRepository.save(testSeat);
    }

    @Test
    @DisplayName("정상적인 좌석 예약 전체 플로우 테스트")
    void fullReservationFlow_ShouldWorkCorrectly() {
        // given
        String userId = "user-123";
        ReserveSeatCommand command = new ReserveSeatCommand(userId, concertId, seatNumber);

        // when - 예약 요청
        ReservationResult result = reserveSeatUseCase.reserveSeat(command);

        // then - 예약 결과 확인
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getConcertId()).isEqualTo(concertId);
        assertThat(result.getSeatNumber()).isEqualTo(seatNumber);
        assertThat(result.getPrice()).isEqualTo(50000);
        assertThat(result.getRemainingTimeSeconds()).isGreaterThan(0);

        // DB 상태 확인
        Seat updatedSeat = seatJpaRepository.findById(testSeat.getSeatId()).orElseThrow();
        assertThat(updatedSeat.getStatus()).isEqualTo(Seat.SeatStatus.TEMPORARILY_ASSIGNED);
        assertThat(updatedSeat.getAssignedUserId()).isEqualTo(userId);
        assertThat(updatedSeat.getAssignedUntil()).isAfter(LocalDateTime.now());

        // 예약 상태 조회
        ReservationResult statusResult = reserveSeatUseCase.getReservationStatus(result.getReservationId());
        assertThat(statusResult.getReservationId()).isEqualTo(result.getReservationId());
    }

    @Test
    @DisplayName("동일한 좌석을 두 번 예약하면 두 번째는 실패해야 한다")
    void duplicateReservation_ShouldFail() {
        // given
        String firstUserId = "user-123";
        String secondUserId = "user-456";

        ReserveSeatCommand firstCommand = new ReserveSeatCommand(firstUserId, concertId, seatNumber);
        ReserveSeatCommand secondCommand = new ReserveSeatCommand(secondUserId, concertId, seatNumber);

        // when - 첫 번째 예약 성공
        ReservationResult firstResult = reserveSeatUseCase.reserveSeat(firstCommand);
        assertThat(firstResult).isNotNull();

        // then - 두 번째 예약 실패
        assertThatThrownBy(() -> reserveSeatUseCase.reserveSeat(secondCommand))
                .isInstanceOf(SeatNotAvailableException.class);
    }

    @Test
    @DisplayName("예약 취소 후 다른 사용자가 예약할 수 있어야 한다")
    void cancelAndReReserve_ShouldWork() {
        // given
        String firstUserId = "user-123";
        String secondUserId = "user-456";

        ReserveSeatCommand firstCommand = new ReserveSeatCommand(firstUserId, concertId, seatNumber);
        ReservationResult firstResult = reserveSeatUseCase.reserveSeat(firstCommand);

        // when - 첫 번째 사용자가 예약 취소
        reserveSeatUseCase.cancelReservation(firstResult.getReservationId(), firstUserId);

        // then - 두 번째 사용자가 예약 가능
        ReserveSeatCommand secondCommand = new ReserveSeatCommand(secondUserId, concertId, seatNumber);
        ReservationResult secondResult = reserveSeatUseCase.reserveSeat(secondCommand);

        assertThat(secondResult).isNotNull();
        assertThat(secondResult.getUserId()).isEqualTo(secondUserId);
        assertThat(secondResult.getSeatNumber()).isEqualTo(seatNumber);
    }

    @Test
    @DisplayName("만료된 예약 자동 해제 테스트")
    void expiredReservationRelease_ShouldWork() {
        // given - 과거 시간으로 만료된 예약 생성
        Reservation expiredReservation = new Reservation(
                "user-123", concertId, testSeat.getSeatId(), 50000,
                LocalDateTime.now().minusMinutes(1) // 1분 전에 만료
        );
        reservationJpaRepository.save(expiredReservation);

        // 좌석도 임시 배정 상태로 변경
        testSeat.assignTemporarily("user-123", LocalDateTime.now().minusMinutes(1));
        seatJpaRepository.save(testSeat);

        // when - 만료된 예약 해제 실행
        reserveSeatUseCase.releaseExpiredReservations();

        // then - 좌석이 다시 예약 가능해야 함
        String newUserId = "user-456";
        ReserveSeatCommand command = new ReserveSeatCommand(newUserId, concertId, seatNumber);

        ReservationResult result = reserveSeatUseCase.reserveSeat(command);
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(newUserId);
    }
}