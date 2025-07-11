package kr.hhplus.be.server.reservation.service;

import kr.hhplus.be.server.common.lock.DistributedLockService;
import kr.hhplus.be.server.external.client.DataPlatformClient;
import kr.hhplus.be.server.reservation.command.ReserveSeatCommand;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.dto.ReservationResult;
import kr.hhplus.be.server.reservation.repository.ReservationRepository;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional
class EventBasedReservationServiceIntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @MockitoBean
    private SeatRepository seatRepository;

    @MockitoBean
    private ReservationRepository reservationRepository;

    @MockitoBean
    private DistributedLockService distributedLockService;

    @MockitoBean
    private DataPlatformClient dataPlatformClient;

    @Test
    void 예약_성공_시_이벤트가_발행되어_데이터_플랫폼으로_전송된다() throws InterruptedException {
        // Given
        String userId = "user123";
        Long concertId = 1L;
        Integer seatNumber = 10;
        Long seatId = 100L;
        BigDecimal price = new BigDecimal("50000");

        Seat mockSeat = createMockSeat(seatId, concertId, seatNumber, price);
        Reservation mockReservation = createMockReservation(userId, concertId, seatId, price);

        // Mock 설정
        given(distributedLockService.tryLock(any(), any(), anyLong())).willReturn(true);
        given(seatRepository.findByConcertIdAndSeatNumberWithLock(concertId, seatNumber))
                .willReturn(Optional.of(mockSeat));
        given(seatRepository.save(any(Seat.class))).willReturn(mockSeat);
        given(reservationRepository.save(any(Reservation.class))).willReturn(mockReservation);

        // When
        ReserveSeatCommand command = new ReserveSeatCommand(userId, concertId, seatNumber);
        ReservationResult result = reservationService.reserveSeat(command);

        // Then
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getConcertId()).isEqualTo(concertId);
        assertThat(result.getSeatNumber()).isEqualTo(seatNumber);

        // 이벤트가 비동기로 처리되므로 잠시 대기
        TimeUnit.MILLISECONDS.sleep(100);

        // 데이터 플랫폼 클라이언트가 호출되었는지 검증
        verify(dataPlatformClient, timeout(1000)).sendReservationData(any());
        verify(distributedLockService).unlock(any(), any());
    }

    @Test
    void 좌석이_이미_배정된_경우_예외가_발생하고_데이터_전송은_되지_않는다() {
        // Given
        String userId = "user123";
        Long concertId = 1L;
        Integer seatNumber = 10;
        Long seatId = 100L;
        BigDecimal price = new BigDecimal("50000");

        // 이미 배정된 좌석 Mock 생성
        Seat mockSeat = mock(Seat.class);
        given(mockSeat.getSeatId()).willReturn(seatId);
        given(mockSeat.getConcertId()).willReturn(concertId);
        given(mockSeat.getSeatNumber()).willReturn(seatNumber);
        given(mockSeat.getPrice()).willReturn(price);
        given(mockSeat.isAvailable()).willReturn(false); // 이미 배정된 상태
        given(mockSeat.isExpired()).willReturn(false);   // 만료되지 않은 상태

        given(distributedLockService.tryLock(any(), any(), anyLong())).willReturn(true);
        given(seatRepository.findByConcertIdAndSeatNumberWithLock(concertId, seatNumber))
                .willReturn(Optional.of(mockSeat));

        // When & Then
        ReserveSeatCommand command = new ReserveSeatCommand(userId, concertId, seatNumber);
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reservationService.reserveSeat(command));

        assertThat(exception.getMessage()).isEqualTo("이미 다른 사용자가 선택한 좌석입니다.");

        // 데이터 플랫폼으로 전송되지 않았는지 검증
        verify(dataPlatformClient, never()).sendReservationData(any());

        // 락이 해제되었는지 검증 (finally 블록에서 실행됨)
        verify(distributedLockService).unlock(any(), any());
    }

    @Test
    void 분산_락_획득_실패_시_예외가_발생하고_데이터_전송은_되지_않는다() {
        // Given
        String userId = "user123";
        Long concertId = 1L;
        Integer seatNumber = 10;

        // 락 획득 실패 시뮬레이션
        given(distributedLockService.tryLock(any(), any(), anyLong())).willReturn(false);

        // When & Then
        ReserveSeatCommand command = new ReserveSeatCommand(userId, concertId, seatNumber);
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reservationService.reserveSeat(command));

        assertThat(exception.getMessage()).isEqualTo("다른 사용자가 처리 중입니다. 잠시 후 재시도해주세요.");

        // 좌석 조회가 발생하지 않았는지 검증
        verify(seatRepository, never()).findByConcertIdAndSeatNumberWithLock(anyLong(), any());

        // 데이터 플랫폼으로 전송되지 않았는지 검증
        verify(dataPlatformClient, never()).sendReservationData(any());

        // 락 해제는 호출되지 않음 (락 획득 실패했으므로)
        verify(distributedLockService, never()).unlock(any(), any());
    }

    @Test
    void 데이터_전송_실패_시_예약은_유지된다() throws InterruptedException {
        // Given
        String userId = "user123";
        Long concertId = 1L;
        Integer seatNumber = 10;
        Long seatId = 100L;
        BigDecimal price = new BigDecimal("50000");

        Seat mockSeat = createMockSeat(seatId, concertId, seatNumber, price);
        Reservation mockReservation = createMockReservation(userId, concertId, seatId, price);

        given(distributedLockService.tryLock(any(), any(), anyLong())).willReturn(true);
        given(seatRepository.findByConcertIdAndSeatNumberWithLock(concertId, seatNumber))
                .willReturn(Optional.of(mockSeat));
        given(seatRepository.save(any(Seat.class))).willReturn(mockSeat);
        given(reservationRepository.save(any(Reservation.class))).willReturn(mockReservation);

        // 데이터 플랫폼 전송 실패 시뮬레이션
        doThrow(new RuntimeException("네트워크 오류"))
                .when(dataPlatformClient).sendReservationData(any());

        // When
        ReserveSeatCommand command = new ReserveSeatCommand(userId, concertId, seatNumber);
        ReservationResult result = reservationService.reserveSeat(command);

        // Then
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getConcertId()).isEqualTo(concertId);

        // 예약은 정상적으로 완료되었지만, 데이터 전송은 실패
        verify(reservationRepository).save(any(Reservation.class));
        verify(dataPlatformClient, timeout(1000)).sendReservationData(any());
        verify(distributedLockService).unlock(any(), any());
    }

    @Test
    void 만료된_좌석_배정은_해제되고_새로운_예약이_성공한다() throws InterruptedException {
        // Given
        String userId = "user123";
        Long concertId = 1L;
        Integer seatNumber = 10;
        Long seatId = 100L;
        BigDecimal price = new BigDecimal("50000");

        // 만료된 좌석 Mock 생성
        Seat mockSeat = mock(Seat.class);
        given(mockSeat.getSeatId()).willReturn(seatId);
        given(mockSeat.getConcertId()).willReturn(concertId);
        given(mockSeat.getSeatNumber()).willReturn(seatNumber);
        given(mockSeat.getPrice()).willReturn(price);
        given(mockSeat.isAvailable()).willReturn(false); // 배정된 상태
        given(mockSeat.isExpired()).willReturn(true);    // 만료된 상태

        Reservation mockReservation = createMockReservation(userId, concertId, seatId, price);

        given(distributedLockService.tryLock(any(), any(), anyLong())).willReturn(true);
        given(seatRepository.findByConcertIdAndSeatNumberWithLock(concertId, seatNumber))
                .willReturn(Optional.of(mockSeat));
        given(seatRepository.save(any(Seat.class))).willReturn(mockSeat);
        given(reservationRepository.save(any(Reservation.class))).willReturn(mockReservation);

        // When
        ReserveSeatCommand command = new ReserveSeatCommand(userId, concertId, seatNumber);
        ReservationResult result = reservationService.reserveSeat(command);

        // Then
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getConcertId()).isEqualTo(concertId);
        assertThat(result.getSeatNumber()).isEqualTo(seatNumber);

        // 좌석이 두 번 저장되었는지 확인 (해제 + 새로운 배정)
        verify(seatRepository, times(2)).save(any(Seat.class));
        verify(mockSeat).releaseAssignment(); // 만료된 배정 해제
        verify(mockSeat).assignTemporarily(eq(userId), any(LocalDateTime.class)); // 새로운 배정

        // 이벤트가 발행되고 데이터 전송이 수행되는지 검증
        verify(dataPlatformClient, timeout(1000)).sendReservationData(any());
        verify(distributedLockService).unlock(any(), any());
    }

    private Seat createMockSeat(Long seatId, Long concertId, Integer seatNumber, BigDecimal price) {
        Seat seat = mock(Seat.class);
        given(seat.getSeatId()).willReturn(seatId);
        given(seat.getConcertId()).willReturn(concertId);
        given(seat.getSeatNumber()).willReturn(seatNumber);
        given(seat.getPrice()).willReturn(price);
        given(seat.isAvailable()).willReturn(true);  // 사용 가능한 상태
        given(seat.isExpired()).willReturn(false);   // 만료되지 않은 상태
        return seat;
    }

    private Reservation createMockReservation(String userId, Long concertId, Long seatId, BigDecimal price) {
        Reservation reservation = mock(Reservation.class);
        given(reservation.getReservationId()).willReturn("reservation123");
        given(reservation.getUserId()).willReturn(userId);
        given(reservation.getConcertId()).willReturn(concertId);
        given(reservation.getSeatId()).willReturn(seatId);
        given(reservation.getPrice()).willReturn(price);
        return reservation;
    }
}