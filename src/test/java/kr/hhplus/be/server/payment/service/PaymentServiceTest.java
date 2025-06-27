package kr.hhplus.be.server.payment.service;

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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BalanceService balanceService; // UserRepository → BalanceService
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private SeatRepository seatRepository;

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
                BigDecimal.valueOf(50000), // int → BigDecimal
                LocalDateTime.now().plusMinutes(5)
        );
        balance = new Balance("user-123", BigDecimal.valueOf(100000)); // User → Balance
        seat = new Seat(1L, 15, BigDecimal.valueOf(50000)); // 생성자 수정
        seat.assignTemporarily("user-123", LocalDateTime.now().plusMinutes(5));
    }

    @Test
    @DisplayName("정상적인 결제 처리가 성공한다")
    void whenProcessPayment_ThenShouldSucceed() {
        // given
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
        assertThat(result.getAmount()).isEqualTo(BigDecimal.valueOf(50000)); // Long → BigDecimal
        assertThat(result.getStatus()).isEqualTo("COMPLETED");

        // 검증: 잔액 확인 및 차감
        verify(balanceService).hasEnoughBalance("user-123", BigDecimal.valueOf(50000));
        verify(balanceService).deductBalance("user-123", BigDecimal.valueOf(50000), "res-123");

        // 검증: 예약 확정
        verify(reservationRepository).save(argThat(r ->
                r.getStatus() == Reservation.ReservationStatus.CONFIRMED
        ));

        // 검증: 좌석 확정 배정
        verify(seatRepository).save(argThat(s ->
                s.getStatus() == Seat.SeatStatus.RESERVED
        ));

        // 검증: 결제 정보 저장
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("잔액 부족 시 결제가 실패한다")
    void whenProcessPaymentWithInsufficientBalance_ThenShouldThrowException() {
        // given
        given(reservationRepository.findById("res-123")).willReturn(Optional.of(reservation));
        given(balanceService.hasEnoughBalance("user-123", BigDecimal.valueOf(50000))).willReturn(false);
        given(balanceService.getBalanceAmount("user-123")).willReturn(BigDecimal.valueOf(30000));

        // when & then
        assertThatThrownBy(() -> paymentService.processPayment(command))
                .isInstanceOf(InsufficientBalanceException.class);

        // 검증: 잔액 차감이 호출되지 않음
        verify(balanceService, never()).deductBalance(any(), any(), any());
        // 검증: 결제 정보가 저장되지 않음
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("존재하지 않는 예약에 대한 결제 시 예외가 발생한다")
    void whenProcessPaymentForNonExistentReservation_ThenShouldThrowException() {
        // given
        given(reservationRepository.findById("res-123")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> paymentService.processPayment(command))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    @DisplayName("만료된 예약에 대한 결제 시 예외가 발생한다")
    void whenProcessPaymentForExpiredReservation_ThenShouldThrowException() {
        // given
        Reservation expiredReservation = new Reservation(
                "user-123",
                1L,
                1L,
                BigDecimal.valueOf(50000), // int → BigDecimal
                LocalDateTime.now().minusMinutes(1)
        );
        given(reservationRepository.findById("res-123")).willReturn(Optional.of(expiredReservation));

        // when & then
        assertThatThrownBy(() -> paymentService.processPayment(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("예약이 만료되었습니다");
    }
}