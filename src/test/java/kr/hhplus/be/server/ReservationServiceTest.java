package kr.hhplus.be.server;

import kr.hhplus.be.server.command.ReserveSeatCommand;
import kr.hhplus.be.server.domain.Reservation;
import kr.hhplus.be.server.domain.Seat;
import kr.hhplus.be.server.dto.ReservationResult;
import kr.hhplus.be.server.repository.ReservationRepository;
import kr.hhplus.be.server.repository.SeatRepository;
import kr.hhplus.be.server.service.DistributedLockService;
import kr.hhplus.be.server.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private DistributedLockService distributedLockService;

    @InjectMocks
    private ReservationService reservationService;

    private ReserveSeatCommand command;
    private Seat availableSeat;

    @BeforeEach
    void setUp() {
        command = new ReserveSeatCommand("user-123", 1L, 15);
        availableSeat = new Seat(1L, 1L, 15, 50000);
    }

    @Test
    @DisplayName("정상적인 좌석 예약 요청 시 임시 배정이 성공한다")
    void whenReserveSeatWithValidRequest_ThenShouldSucceed() {
        // given
        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(true);
        given(seatRepository.findByConcertIdAndSeatNumber(1L, 15)).willReturn(Optional.of(availableSeat));
        given(seatRepository.save(any(Seat.class))).willReturn(availableSeat);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        ReservationResult result = reservationService.reserveSeat(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getConcertId()).isEqualTo(1L);
        assertThat(result.getSeatNumber()).isEqualTo(15);
        assertThat(result.getPrice()).isEqualTo(50000);
        assertThat(result.getRemainingTimeSeconds()).isGreaterThan(0);

        // 검증: 좌석이 임시 배정되었는지 확인
        verify(seatRepository).save(argThat(seat ->
                seat.getStatus() == Seat.SeatStatus.TEMPORARILY_ASSIGNED &&
                        seat.getAssignedUserId().equals("user-123")
        ));

        // 검증: 예약이 생성되었는지 확인
        verify(reservationRepository).save(argThat(reservation ->
                reservation.getUserId().equals("user-123") &&
                        reservation.getStatus() == Reservation.ReservationStatus.TEMPORARILY_ASSIGNED
        ));

        // 검증: 락이 해제되었는지 확인
        verify(distributedLockService).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("분산 락 획득에 실패하면 예외가 발생한다")
    void whenFailToAcquireLock_ThenShouldThrowException() {
        // given
        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> reservationService.reserveSeat(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("다른 사용자가 처리 중입니다. 잠시 후 재시도해주세요.");

        // 검증: 좌석 조회가 발생하지 않았는지 확인
        verify(seatRepository, never()).findByConcertIdAndSeatNumber(anyLong(), anyInt());
    }

    @Test
    @DisplayName("존재하지 않는 좌석을 예약하려고 하면 예외가 발생한다")
    void whenReserveNonExistentSeat_ThenShouldThrowException() {
        // given
        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(true);
        given(seatRepository.findByConcertIdAndSeatNumber(1L, 15)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reservationService.reserveSeat(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("존재하지 않는 좌석입니다.");

        // 검증: 락이 해제되었는지 확인
        verify(distributedLockService).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("이미 예약된 좌석을 예약하려고 하면 예외가 발생한다")
    void whenReserveAlreadyReservedSeat_ThenShouldThrowException() {
        // given
        Seat reservedSeat = new Seat(1L, 1L, 15, 50000);
        reservedSeat.assignTemporarily("other-user", LocalDateTime.now().plusMinutes(5));

        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(true);
        given(seatRepository.findByConcertIdAndSeatNumber(1L, 15)).willReturn(Optional.of(reservedSeat));

        // when & then
        assertThatThrownBy(() -> reservationService.reserveSeat(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("이미 다른 사용자가 선택한 좌석입니다.");

        // 검증: 락이 해제되었는지 확인
        verify(distributedLockService).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("만료된 임시 배정 좌석은 자동으로 해제하고 예약을 진행한다")
    void whenReserveExpiredTemporarilyAssignedSeat_ThenShouldReleaseAndProceed() {
        // given
        Seat expiredSeat = new Seat(1L, 1L, 15, 50000);
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);
        expiredSeat.assignTemporarily("other-user", pastTime);

        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(true);
        given(seatRepository.findByConcertIdAndSeatNumber(1L, 15)).willReturn(Optional.of(expiredSeat));
        given(seatRepository.save(any(Seat.class))).willReturn(expiredSeat);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        ReservationResult result = reservationService.reserveSeat(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");

        // 검증: 좌석이 두 번 저장되었는지 확인 (해제 + 새로운 배정)
        verify(seatRepository, times(2)).save(any(Seat.class));
    }

    @Test
    @DisplayName("예약 상태 조회가 정상적으로 동작한다")
    void whenGetReservationStatus_ThenShouldReturnCorrectInfo() {
        // given
        String reservationId = "reservation-123";
        Reservation reservation = new Reservation("user-123", 1L, 1L, 50000, LocalDateTime.now().plusMinutes(5));

        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
        given(seatRepository.findById(1L)).willReturn(Optional.of(availableSeat));

        // when
        ReservationResult result = reservationService.getReservationStatus(reservationId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getReservationId()).isEqualTo(reservationId);
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getSeatNumber()).isEqualTo(15);
    }

    @Test
    @DisplayName("존재하지 않는 예약 조회 시 예외가 발생한다")
    void whenGetNonExistentReservation_ThenShouldThrowException() {
        // given
        String reservationId = "non-existent";
        given(reservationRepository.findById(reservationId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reservationService.getReservationStatus(reservationId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("존재하지 않는 예약입니다.");
    }

    @Test
    @DisplayName("예약 취소가 정상적으로 동작한다")
    void whenCancelReservation_ThenShouldSucceed() {
        // given
        String reservationId = "reservation-123";
        String userId = "user-123";

        Reservation reservation = new Reservation(userId, 1L, 1L, 50000, LocalDateTime.now().plusMinutes(5));
        Seat seat = new Seat(1L, 1L, 15, 50000);
        seat.assignTemporarily(userId, LocalDateTime.now().plusMinutes(5));

        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
        given(seatRepository.findById(1L)).willReturn(Optional.of(seat));

        // when
        reservationService.cancelReservation(reservationId, userId);

        // then
        verify(reservationRepository).save(argThat(r ->
                r.getStatus() == Reservation.ReservationStatus.CANCELLED
        ));
        verify(seatRepository).save(argThat(s ->
                s.getStatus() == Seat.SeatStatus.AVAILABLE
        ));
    }

    @Test
    @DisplayName("권한 없는 사용자가 예약 취소를 시도하면 예외가 발생한다")
    void whenUnauthorizedUserTriesToCancel_ThenShouldThrowException() {
        // given
        String reservationId = "reservation-123";
        String reservationOwner = "user-123";
        String unauthorizedUser = "user-456";

        Reservation reservation = new Reservation(reservationOwner, 1L, 1L, 50000, LocalDateTime.now().plusMinutes(5));
        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

        // when & then
        assertThatThrownBy(() -> reservationService.cancelReservation(reservationId, unauthorizedUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("예약을 취소할 권한이 없습니다.");
    }

    @Test
    @DisplayName("만료된 예약들을 일괄 해제한다")
    void whenReleaseExpiredReservations_ThenShouldProcessAllExpiredReservations() {
        // given
        Reservation expiredReservation1 = new Reservation("user-123", 1L, 1L, 50000, LocalDateTime.now().minusMinutes(1));
        Reservation expiredReservation2 = new Reservation("user-456", 1L, 2L, 50000, LocalDateTime.now().minusMinutes(2));

        Seat seat1 = new Seat(1L, 1L, 15, 50000);
        Seat seat2 = new Seat(2L, 1L, 16, 50000);

        given(reservationRepository.findExpiredReservations())
                .willReturn(Arrays.asList(expiredReservation1, expiredReservation2));
        given(seatRepository.findById(1L)).willReturn(Optional.of(seat1));
        given(seatRepository.findById(2L)).willReturn(Optional.of(seat2));

        // when
        reservationService.releaseExpiredReservations();

        // then
        verify(seatRepository, times(2)).save(any(Seat.class));
        verify(reservationRepository).saveAll(argThat(reservations ->
                reservations.size() == 2 &&
                        reservations.stream().allMatch(r -> r.getStatus() == Reservation.ReservationStatus.EXPIRED)
        ));
    }
}
