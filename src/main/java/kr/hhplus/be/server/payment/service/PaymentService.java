package kr.hhplus.be.server.payment.service;

import kr.hhplus.be.server.payment.command.PaymentCommand;
import kr.hhplus.be.server.payment.dto.PaymentResult;
import kr.hhplus.be.server.payment.exception.PaymentNotFoundException;
import kr.hhplus.be.server.reservation.exception.ReservationNotFoundException;
import kr.hhplus.be.server.payment.exception.InsufficientBalanceException;
import kr.hhplus.be.server.payment.domain.Payment;
import kr.hhplus.be.server.payment.repository.PaymentRepository;
import kr.hhplus.be.server.point.repository.BalanceTransactionRepository;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.repository.ReservationRepository;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatRepository;
import kr.hhplus.be.server.user.repository.UserRepository;
import kr.hhplus.be.server.point.domain.BalanceTransaction;
import kr.hhplus.be.server.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService implements PaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final BalanceTransactionRepository balanceTransactionRepository;

    @Override
    @Transactional
    public PaymentResult processPayment(PaymentCommand command) {
        log.info("결제 처리 시작: reservationId={}, userId={}",
                command.getReservationId(), command.getUserId());

        // 1. 예약 정보 확인
        Reservation reservation = reservationRepository.findById(command.getReservationId())
                .orElseThrow(() -> new ReservationNotFoundException(command.getReservationId()));

        validateReservation(reservation, command.getUserId());

        // 2. 사용자 잔액 확인 및 차감 (비관적 락)
        Long paymentAmount = reservation.getPrice().longValue();
        int updatedRows = userRepository.deductBalanceWithCondition(command.getUserId(), paymentAmount);

        if (updatedRows == 0) {
            // 차감 실패 = 잔액 부족 또는 사용자 없음
            User user = userRepository.findById(command.getUserId())
                    .orElseThrow(() -> new RuntimeException("사용자 정보를 찾을 수 없습니다."));

            throw new InsufficientBalanceException(user.getBalance(), paymentAmount);
        }

        // 차감 후 현재 잔액 조회 (거래 내역용)
        User user = userRepository.findById(command.getUserId())
                .orElseThrow(() -> new RuntimeException("사용자 정보를 찾을 수 없습니다."));

        // 3. 결제 정보 생성
        Payment payment = new Payment(
                command.getReservationId(),
                command.getUserId(),
                reservation.getPrice().longValue(),
                Payment.PaymentMethod.BALANCE
        );
        paymentRepository.save(payment);

        // 4. 예약 확정
        reservation.confirm(LocalDateTime.now());
        reservationRepository.save(reservation);

        // 5. 좌석 확정 배정
        Seat seat = seatRepository.findById(reservation.getSeatId())
                .orElseThrow(() -> new RuntimeException("좌석 정보를 찾을 수 없습니다."));
        seat.confirmReservation(LocalDateTime.now());
        seatRepository.save(seat);

        // 6. 거래 내역 저장
        BalanceTransaction transaction = BalanceTransaction.payment(
                command.getUserId(),
                reservation.getPrice().longValue(),
                user.getBalance(),
                command.getReservationId()
        );
        balanceTransactionRepository.save(transaction);

        log.info("결제 처리 완료: paymentId={}, amount={}, balanceAfter={}",
                payment.getPaymentId(), payment.getAmount(), user.getBalance());

        return new PaymentResult(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResult getPaymentInfo(String paymentId) {
        log.debug("결제 정보 조회: paymentId={}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        return new PaymentResult(payment);
    }

    private void validateReservation(Reservation reservation, String userId) {
        // 예약자 확인
        if (!reservation.getUserId().equals(userId)) {
            throw new RuntimeException("예약자가 일치하지 않습니다.");
        }

        // 예약 상태 확인
        if (reservation.getStatus() != Reservation.ReservationStatus.TEMPORARILY_ASSIGNED) {
            throw new RuntimeException("결제할 수 없는 예약 상태입니다. 현재 상태: " + reservation.getStatus());
        }

        // 예약 만료 확인
        if (reservation.isExpired()) {
            throw new RuntimeException("예약이 만료되었습니다. 다시 예약해주세요.");
        }
    }
}
