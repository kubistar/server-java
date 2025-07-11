package kr.hhplus.be.server.reservation.service;

import kr.hhplus.be.server.reservation.command.ReserveSeatCommand;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.event.ReservationCompletedEvent;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.reservation.dto.ReservationResult;
import kr.hhplus.be.server.reservation.repository.ReservationRepository;
import kr.hhplus.be.server.seat.repository.SeatRepository;
import kr.hhplus.be.server.common.lock.DistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.math.BigDecimal;
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

    @Mock
    private ApplicationEventPublisher eventPublisher; // 이벤트 퍼블리셔 추가

    @InjectMocks
    private ReservationService reservationService;

    private ReserveSeatCommand command;
    private Seat availableSeat;
    private BigDecimal seatPrice = BigDecimal.valueOf(50000);

    @BeforeEach
    void setUp() {
        command = new ReserveSeatCommand("user-123", 1L, 15);
        availableSeat = new Seat(1L, 15, BigDecimal.valueOf(50000));

        // Reflection으로 seatId 설정
        try {
            Field seatIdField = Seat.class.getDeclaredField("seatId");
            seatIdField.setAccessible(true);
            seatIdField.set(availableSeat, 1L); // seatId를 1L로 설정
        } catch (Exception e) {
            throw new RuntimeException("Test setup failed", e);
        }

        // 객체 상태 확인
        assertThat(command.getUserId()).isNotNull();
        assertThat(command.getConcertId()).isNotNull();
        assertThat(command.getSeatNumber()).isNotNull();
    }

    private void setSeatId(Seat seat, Long seatId) {
        try {
            Field idField = Seat.class.getDeclaredField("seatId");
            idField.setAccessible(true);
            idField.set(seat, seatId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set seatId", e);
        }
    }

    @Test
    @DisplayName("정상적인 좌석 예약 요청 시 임시 배정이 성공하고 이벤트가 발행된다")
    void whenReserveSeatWithValidRequest_ThenShouldSucceedAndPublishEvent() {
        // given
        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(true);
        given(seatRepository.findByConcertIdAndSeatNumberWithLock(1L, 15)).willReturn(Optional.of(availableSeat));
        given(seatRepository.save(any(Seat.class))).willReturn(availableSeat);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        ReservationResult result = reservationService.reserveSeat(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getConcertId()).isEqualTo(1L);
        assertThat(result.getSeatNumber()).isEqualTo(15);
        assertThat(result.getPrice()).isEqualTo(BigDecimal.valueOf(50000));
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

        // 검증: 이벤트가 발행되었는지 확인
        ArgumentCaptor<ReservationCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(ReservationCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        ReservationCompletedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getUserId()).isEqualTo("user-123");
        assertThat(publishedEvent.getConcertId()).isEqualTo(1L);
        assertThat(publishedEvent.getSeatNumber()).isEqualTo(15);
        assertThat(publishedEvent.getPrice()).isEqualTo(BigDecimal.valueOf(50000));

        // 검증: 락이 해제되었는지 확인
        verify(distributedLockService).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("분산 락 획득에 실패하면 예외가 발생하고 이벤트는 발행되지 않는다")
    void whenFailToAcquireLock_ThenShouldThrowExceptionAndNotPublishEvent() {
        // given
        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> reservationService.reserveSeat(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("다른 사용자가 처리 중입니다. 잠시 후 재시도해주세요.");

        // 검증: 좌석 조회가 발생하지 않았는지 확인
        verify(seatRepository, never()).findByConcertIdAndSeatNumberWithLock(anyLong(), anyInt());

        // 검증: 이벤트가 발행되지 않았는지 확인
        verify(eventPublisher, never()).publishEvent(any(ReservationCompletedEvent.class));
    }

    @Test
    @DisplayName("존재하지 않는 좌석을 예약하려고 하면 예외가 발생하고 이벤트는 발행되지 않는다")
    void whenReserveNonExistentSeat_ThenShouldThrowExceptionAndNotPublishEvent() {
        // given
        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(true);
        given(seatRepository.findByConcertIdAndSeatNumberWithLock(1L, 15)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reservationService.reserveSeat(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("존재하지 않는 좌석입니다.");

        // 검증: 락이 해제되었는지 확인
        verify(distributedLockService).unlock(anyString(), anyString());

        // 검증: 이벤트가 발행되지 않았는지 확인
        verify(eventPublisher, never()).publishEvent(any(ReservationCompletedEvent.class));
    }

    @Test
    @DisplayName("이미 예약된 좌석을 예약하려고 하면 예외가 발생하고 이벤트는 발행되지 않는다")
    void whenReserveAlreadyReservedSeat_ThenShouldThrowExceptionAndNotPublishEvent() {
        // given
        Seat reservedSeat = new Seat(1L, 15, seatPrice);
        reservedSeat.assignTemporarily("other-user", LocalDateTime.now().plusMinutes(5));

        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(true);
        given(seatRepository.findByConcertIdAndSeatNumberWithLock(1L, 15)).willReturn(Optional.of(reservedSeat));

        // when & then
        assertThatThrownBy(() -> reservationService.reserveSeat(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("이미 다른 사용자가 선택한 좌석입니다.");

        // 검증: 락이 해제되었는지 확인
        verify(distributedLockService).unlock(anyString(), anyString());

        // 검증: 이벤트가 발행되지 않았는지 확인
        verify(eventPublisher, never()).publishEvent(any(ReservationCompletedEvent.class));
    }

    @Test
    @DisplayName("만료된 임시 배정 좌석은 자동으로 해제하고 예약을 진행한 후 이벤트를 발행한다")
    void whenReserveExpiredTemporarilyAssignedSeat_ThenShouldReleaseAndProceedAndPublishEvent() {
        // given
        Seat expiredSeat = new Seat(1L, 15, seatPrice);

        // seatId 설정
        setSeatId(expiredSeat, 1L);

        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);
        expiredSeat.assignTemporarily("other-user", pastTime);

        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(true);
        given(seatRepository.findByConcertIdAndSeatNumberWithLock(1L, 15)).willReturn(Optional.of(expiredSeat));
        given(seatRepository.save(any(Seat.class))).willReturn(expiredSeat);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        ReservationResult result = reservationService.reserveSeat(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");

        // 검증: 좌석이 두 번 저장되었는지 확인 (해제 + 새로운 배정)
        verify(seatRepository, times(2)).save(any(Seat.class));

        // 검증: 이벤트가 발행되었는지 확인
        verify(eventPublisher).publishEvent(any(ReservationCompletedEvent.class));
    }

    @Test
    @DisplayName("예약 상태 조회가 정상적으로 동작한다")
    void whenGetReservationStatus_ThenShouldReturnCorrectInfo() {
        // given
        String reservationId = "reservation-123";
        Reservation reservation = new Reservation("user-123", 1L, 1L, seatPrice, LocalDateTime.now().plusMinutes(5));

        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
        given(seatRepository.findById(1L)).willReturn(Optional.of(availableSeat));

        // when
        ReservationResult result = reservationService.getReservationStatus(reservationId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getReservationId()).isEqualTo(reservation.getReservationId());
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getSeatNumber()).isEqualTo(15);

        // 조회는 이벤트 발행하지 않음
        verify(eventPublisher, never()).publishEvent(any());
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

        // 실패 시에도 이벤트 발행하지 않음
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("예약 취소가 정상적으로 동작한다")
    void whenCancelReservation_ThenShouldSucceed() {
        // given
        String reservationId = "reservation-123";
        String userId = "user-123";

        Reservation reservation = new Reservation(userId, 1L, 1L, seatPrice, LocalDateTime.now().plusMinutes(5));
        Seat seat = new Seat(1L, 15, seatPrice);
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

        // 취소는 별도 이벤트 없음 (필요시 CancellationEvent 추가 가능)
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("권한 없는 사용자가 예약 취소를 시도하면 예외가 발생한다")
    void whenUnauthorizedUserTriesToCancel_ThenShouldThrowException() {
        // given
        String reservationId = "reservation-123";
        String reservationOwner = "user-123";
        String unauthorizedUser = "user-456";

        Reservation reservation = new Reservation(reservationOwner, 1L, 1L, seatPrice, LocalDateTime.now().plusMinutes(5));
        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

        // when & then
        assertThatThrownBy(() -> reservationService.cancelReservation(reservationId, unauthorizedUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("예약을 취소할 권한이 없습니다.");

        // 실패 시에도 이벤트 발행하지 않음
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("만료된 예약들을 일괄 해제한다")
    void whenReleaseExpiredReservations_ThenShouldProcessAllExpiredReservations() {
        // given - 실제 사용되는 것만 stubbing
        Reservation expiredReservation1 = mock(Reservation.class);
        Reservation expiredReservation2 = mock(Reservation.class);

        // 실제로 서비스에서 호출되는 메서드들만 설정
        when(expiredReservation1.getSeatId()).thenReturn(1L);
        when(expiredReservation2.getSeatId()).thenReturn(2L);

        Seat seat1 = new Seat(1L, 15, seatPrice);
        Seat seat2 = new Seat(1L, 16, seatPrice);
        setSeatId(seat1, 1L);
        setSeatId(seat2, 2L);

        seat1.assignTemporarily("user-123", LocalDateTime.now().minusMinutes(1));
        seat2.assignTemporarily("user-456", LocalDateTime.now().minusMinutes(2));

        given(reservationRepository.findByStatusAndExpiresAtBefore(
                eq(Reservation.ReservationStatus.TEMPORARILY_ASSIGNED),
                any(LocalDateTime.class)))
                .willReturn(Arrays.asList(expiredReservation1, expiredReservation2));

        given(seatRepository.findById(1L)).willReturn(Optional.of(seat1));
        given(seatRepository.findById(2L)).willReturn(Optional.of(seat2));

        // when
        reservationService.releaseExpiredReservations();

        // then
        verify(reservationRepository).findByStatusAndExpiresAtBefore(
                eq(Reservation.ReservationStatus.TEMPORARILY_ASSIGNED),
                any(LocalDateTime.class));
        verify(seatRepository, times(2)).findById(any());
        verify(seatRepository, times(2)).save(any(Seat.class));
        verify(reservationRepository).saveAll(any());

        // 만료 처리는 별도 이벤트 없음 (필요시 ExpirationEvent 추가 가능)
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("이벤트 발행 시 올바른 데이터가 포함되어야 한다")
    void whenPublishEvent_ThenShouldContainCorrectData() {
        // given
        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(true);
        given(seatRepository.findByConcertIdAndSeatNumberWithLock(1L, 15)).willReturn(Optional.of(availableSeat));
        given(seatRepository.save(any(Seat.class))).willReturn(availableSeat);

        Reservation mockReservation = mock(Reservation.class);
        when(mockReservation.getReservationId()).thenReturn("reservation-123");
        when(mockReservation.getUserId()).thenReturn("user-123");
        when(mockReservation.getConcertId()).thenReturn(1L);
        given(reservationRepository.save(any(Reservation.class))).willReturn(mockReservation);

        // when
        reservationService.reserveSeat(command);

        // then - 이벤트 내용 상세 검증
        ArgumentCaptor<ReservationCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(ReservationCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        ReservationCompletedEvent event = eventCaptor.getValue();
        assertThat(event.getReservationId()).isEqualTo("reservation-123");
        assertThat(event.getUserId()).isEqualTo("user-123");
        assertThat(event.getConcertId()).isEqualTo(1L);
        assertThat(event.getSeatId()).isEqualTo(1L);
        assertThat(event.getSeatNumber()).isEqualTo(15);
        assertThat(event.getPrice()).isEqualTo(BigDecimal.valueOf(50000));
        assertThat(event.getReservedAt()).isNotNull();
        assertThat(event.getReservedAt()).isBefore(LocalDateTime.now().plusSeconds(1));
    }
}