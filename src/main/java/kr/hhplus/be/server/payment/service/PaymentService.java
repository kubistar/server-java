package kr.hhplus.be.server.payment.service;

import kr.hhplus.be.server.payment.command.PaymentCommand;
import kr.hhplus.be.server.payment.dto.PaymentResult;
import kr.hhplus.be.server.payment.exception.PaymentNotFoundException;
import kr.hhplus.be.server.reservation.exception.ReservationNotFoundException;
import kr.hhplus.be.server.payment.exception.InsufficientBalanceException;
import kr.hhplus.be.server.payment.domain.Payment;
import kr.hhplus.be.server.payment.repository.PaymentRepository;
import kr.hhplus.be.server.balance.service.BalanceService;
import kr.hhplus.be.server.balance.domain.Balance;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.repository.ReservationRepository;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService implements PaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final BalanceService balanceService;  // UserRepository → BalanceService 변경
    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;

    @Override
    @Transactional
    public PaymentResult processPayment(PaymentCommand command) {
        log.info("결제 처리 시작: reservationId={}, userId={}",
                command.getReservationId(), command.getUserId());

        // 1. 예약 정보 확인
        Reservation reservation = reservationRepository.findById(command.getReservationId())
                .orElseThrow(() -> new ReservationNotFoundException(command.getReservationId()));

        validateReservation(reservation, command.getUserId());

        // 2. 결제 금액 설정
        BigDecimal paymentAmount = reservation.getPrice();

        // 3. 잔액 확인
        if (!balanceService.hasEnoughBalance(command.getUserId(), paymentAmount)) {
            BigDecimal currentBalance = balanceService.getBalanceAmount(command.getUserId());
            throw new InsufficientBalanceException(currentBalance.longValue(), paymentAmount.longValue());
        }

        // 4. 잔액 차감 (비관적 락 사용)
        Balance updatedBalance = balanceService.deductBalance(
                command.getUserId(),
                paymentAmount,
                command.getReservationId()
        );

        // 5. 결제 정보 생성
        Payment payment = new Payment(
                command.getReservationId(),
                command.getUserId(),
                paymentAmount,  // BigDecimal 그대로 사용
                Payment.PaymentMethod.BALANCE
        );
        paymentRepository.save(payment);

        // 6. 예약 확정
        reservation.confirm(LocalDateTime.now());
        reservationRepository.save(reservation);

        // 7. 좌석 확정 배정
        Seat seat = seatRepository.findById(reservation.getSeatId())
                .orElseThrow(() -> new RuntimeException("좌석 정보를 찾을 수 없습니다."));
        seat.confirmReservation(LocalDateTime.now());
        seatRepository.save(seat);

        log.info("결제 처리 완료: paymentId={}, amount={}, balanceAfter={}",
                payment.getPaymentId(), payment.getAmount(), updatedBalance.getAmount());

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

    /**
     * 환불 처리
     */
    @Transactional
    public PaymentResult refundPayment(String paymentId, String reason) {
        log.info("환불 처리 시작: paymentId={}, reason={}", paymentId, reason);

        // 1. 결제 정보 조회
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (!payment.isCompleted()) {
            throw new RuntimeException("완료된 결제만 환불 가능합니다.");
        }

        // 2. 환불 금액 추가
        balanceService.refundBalance(payment.getUserId(), payment.getAmount(), reason);

        // 3. 결제 상태 변경
        payment.markAsCancelled();
        paymentRepository.save(payment);

        log.info("환불 처리 완료: paymentId={}, amount={}", paymentId, payment.getAmount());

        return new PaymentResult(payment);
    }

    /**
     * 사용자별 결제 내역 조회
     */
    @Transactional(readOnly = true)
    public PaymentResult getUserPaymentHistory(String userId) {
        // PaymentRepository에 사용자별 조회 메서드가 있다면 사용
        // 또는 페이징 처리된 결과 반환

        log.debug("사용자 결제 내역 조회: userId={}", userId);

        // 구현 필요 시 PaymentRepository에 메서드 추가
        // List<Payment> payments = paymentRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return null; // 실제 구현 시 적절한 결과 반환
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