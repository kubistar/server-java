package kr.hhplus.be.server.payment.service;

import kr.hhplus.be.server.common.lock.DistributedLockService;
import kr.hhplus.be.server.payment.command.PaymentCommand;
import kr.hhplus.be.server.payment.dto.PaymentResult;
import kr.hhplus.be.server.payment.exception.InsufficientBalanceException;
import kr.hhplus.be.server.reservation.exception.ReservationNotFoundException;
import kr.hhplus.be.server.payment.domain.Payment;
import kr.hhplus.be.server.payment.repository.PaymentRepository;
import kr.hhplus.be.server.balance.service.BalanceService;
import kr.hhplus.be.server.balance.domain.Balance;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.repository.ReservationRepository;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BalanceService balanceService;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private DistributedLockService distributedLockService;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentCommand command;
    private Reservation reservation;
    private Balance balance;
    private Seat seat;

    @BeforeEach
    void setUp() {
        command = new PaymentCommand("res-123", "user-123");
        reservation = new Reservation(
                "user-123",
                1L,
                1L,
                BigDecimal.valueOf(50000),
                LocalDateTime.now().plusMinutes(5)
        );
        balance = new Balance("user-123", BigDecimal.valueOf(100000));
        seat = new Seat(1L, 15, BigDecimal.valueOf(50000));
        seat.assignTemporarily("user-123", LocalDateTime.now().plusMinutes(5));

        // setUp에서는 객체 생성만, stubbing은 각 테스트에서 개별 설정
    }

    @Test
    @DisplayName("정상적인 결제 처리가 성공한다")
    void whenProcessPayment_ThenShouldSucceed() {
        // given - 이 테스트에서 필요한 stubbing만 설정
        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(true);
        willDoNothing().given(distributedLockService).unlock(anyString(), anyString());

        given(reservationRepository.findById("res-123")).willReturn(Optional.of(reservation));
        given(balanceService.hasEnoughBalance("user-123", BigDecimal.valueOf(50000))).willReturn(true);
        given(balanceService.deductBalance("user-123", BigDecimal.valueOf(50000), "res-123")).willReturn(balance);
        given(seatRepository.findById(1L)).willReturn(Optional.of(seat));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(seatRepository.save(any(Seat.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        PaymentResult result = paymentService.processPayment(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getReservationId()).isEqualTo("res-123");
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getAmount()).isEqualTo(BigDecimal.valueOf(50000));
        assertThat(result.getStatus()).isEqualTo("COMPLETED");

        // 검증: 핵심 비즈니스 로직만 검증
        verify(balanceService).hasEnoughBalance("user-123", BigDecimal.valueOf(50000));
        verify(balanceService).deductBalance("user-123", BigDecimal.valueOf(50000), "res-123");
        verify(paymentRepository, times(2)).save(any(Payment.class));
    }

    @Test
    @DisplayName("잔액 부족 시 결제가 실패한다")
    void whenProcessPaymentWithInsufficientBalance_ThenShouldThrowException() {
        // given - 이 테스트에서 필요한 stubbing만
        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(true);
        willDoNothing().given(distributedLockService).unlock(anyString(), anyString());

        given(reservationRepository.findById("res-123")).willReturn(Optional.of(reservation));
        given(balanceService.hasEnoughBalance("user-123", BigDecimal.valueOf(50000))).willReturn(false);
        given(balanceService.getBalanceAmount("user-123")).willReturn(BigDecimal.valueOf(30000));

        // when & then
        assertThatThrownBy(() -> paymentService.processPayment(command))
                .isInstanceOf(InsufficientBalanceException.class);

        // 검증: 잔액 차감이 호출되지 않음
        verify(balanceService, never()).deductBalance(any(), any(), any());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("분산 락 획득 실패 시 예외가 발생한다")
    void whenFailToAcquireLock_ThenShouldThrowException() {
        // given - 락 실패만 stubbing
        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> paymentService.processPayment(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결제가 이미 진행 중입니다");

        // 검증: 락 실패 시 다른 로직은 실행되지 않음
        verify(distributedLockService, times(1)).tryLock(anyString(), anyString(), anyLong());
        verify(distributedLockService, never()).unlock(anyString(), anyString());
        verify(reservationRepository, never()).findById(any());
        verify(balanceService, never()).hasEnoughBalance(any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 예약에 대한 결제 시 예외가 발생한다")
    void whenProcessPaymentForNonExistentReservation_ThenShouldThrowException() {
        // given
        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(true);
        willDoNothing().given(distributedLockService).unlock(anyString(), anyString());

        given(reservationRepository.findById("res-123")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> paymentService.processPayment(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("예약 정보를 찾을 수 없습니다");

        // 검증: 예약 조회까지만 실행됨
        verify(reservationRepository).findById("res-123");
        verify(balanceService, never()).hasEnoughBalance(any(), any());
    }

    @Test
    @DisplayName("만료된 예약에 대한 결제 시 예외가 발생한다")
    void whenProcessPaymentForExpiredReservation_ThenShouldThrowException() throws Exception {
        // given
        given(distributedLockService.tryLock(anyString(), anyString(), anyLong())).willReturn(true);
        willDoNothing().given(distributedLockService).unlock(anyString(), anyString());

        Reservation expiredReservation = new Reservation(
                "user-123",
                1L,
                1L,
                BigDecimal.valueOf(50000),
                LocalDateTime.now().plusNanos(1000000) // 1밀리초
        );

        Thread.sleep(10); // 10밀리초 대기하여 만료시킴
        assertTrue(expiredReservation.isExpired());

        given(reservationRepository.findById("res-123")).willReturn(Optional.of(expiredReservation));

        // when & then
        assertThatThrownBy(() -> paymentService.processPayment(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("만료된 예약입니다");

        // 검증: 예약 검증까지만 실행됨
        verify(reservationRepository).findById("res-123");
        verify(balanceService, never()).hasEnoughBalance(any(), any());
    }
}