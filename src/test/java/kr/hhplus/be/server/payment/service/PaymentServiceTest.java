package kr.hhplus.be.server.payment.service;

import kr.hhplus.be.server.payment.command.PaymentCommand;
import kr.hhplus.be.server.domain.*;
import kr.hhplus.be.server.payment.dto.PaymentResult;
import kr.hhplus.be.server.payment.exception.InsufficientBalanceException;
import kr.hhplus.be.server.reservation.exception.ReservationNotFoundException;
import kr.hhplus.be.server.payment.domain.Payment;
import kr.hhplus.be.server.payment.repository.PaymentRepository;
import kr.hhplus.be.server.point.repository.BalanceTransactionRepository;
import kr.hhplus.be.server.repository.*;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.repository.ReservationRepository;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatRepository;
import kr.hhplus.be.server.user.repository.UserRepository;
import kr.hhplus.be.server.point.domain.BalanceTransaction;
import kr.hhplus.be.server.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private UserRepository userRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private BalanceTransactionRepository balanceTransactionRepository;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentCommand command;
    private Reservation reservation;
    private User user;
    private Seat seat;

    @BeforeEach
    void setUp() {
        command = new PaymentCommand("res-123", "user-123");
        reservation = new Reservation("user-123", 1L, 1L, 50000, LocalDateTime.now().plusMinutes(5));
        user = new User("user-123", 100000L);
        seat = new Seat(1L, 1L, 15, 50000);
        seat.assignTemporarily("user-123", LocalDateTime.now().plusMinutes(5));
    }

    @Test
    @DisplayName("정상적인 결제 처리가 성공한다")
    void whenProcessPayment_ThenShouldSucceed() {
        // given
        given(reservationRepository.findById("res-123")).willReturn(Optional.of(reservation));
        given(userRepository.findByIdForUpdate("user-123")).willReturn(Optional.of(user));
        given(seatRepository.findById(1L)).willReturn(Optional.of(seat));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(seatRepository.save(any(Seat.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(balanceTransactionRepository.save(any(BalanceTransaction.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        PaymentResult result = paymentService.processPayment(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getReservationId()).isEqualTo("res-123");
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getAmount()).isEqualTo(50000L);
        assertThat(result.getStatus()).isEqualTo("COMPLETED");

        // 검증: 사용자 잔액 차감
        verify(userRepository).save(argThat(u -> u.getBalance().equals(50000L)));

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

        // 검증: 거래 내역 저장
        verify(balanceTransactionRepository).save(any(BalanceTransaction.class));
    }

    @Test
    @DisplayName("잔액 부족 시 결제가 실패한다")
    void whenProcessPaymentWithInsufficientBalance_ThenShouldThrowException() {
        // given
        User poorUser = new User("user-123", 30000L); // 잔액 부족
        given(reservationRepository.findById("res-123")).willReturn(Optional.of(reservation));
        given(userRepository.findByIdForUpdate("user-123")).willReturn(Optional.of(poorUser));

        // when & then
        assertThatThrownBy(() -> paymentService.processPayment(command))
                .isInstanceOf(InsufficientBalanceException.class);

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
                "user-123", 1L, 1L, 50000, LocalDateTime.now().minusMinutes(1)
        );
        given(reservationRepository.findById("res-123")).willReturn(Optional.of(expiredReservation));

        // when & then
        assertThatThrownBy(() -> paymentService.processPayment(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("예약이 만료되었습니다");
    }
}