package kr.hhplus.be.server.payment.service;

import kr.hhplus.be.server.payment.command.PaymentCommand;
import kr.hhplus.be.server.payment.dto.PaymentResult;
import kr.hhplus.be.server.payment.exception.PaymentNotFoundException;
import kr.hhplus.be.server.payment.exception.InsufficientBalanceException;
import kr.hhplus.be.server.payment.domain.Payment;
import kr.hhplus.be.server.payment.repository.PaymentRepository;
import kr.hhplus.be.server.balance.service.BalanceService;
import kr.hhplus.be.server.balance.domain.Balance;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.repository.ReservationRepository;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatRepository;
import kr.hhplus.be.server.common.lock.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService implements PaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final BalanceService balanceService;
    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final DistributedLockService distributedLockService;

    @Override
    public PaymentResult processPayment(PaymentCommand command) {
        log.info("결제 처리 시작: reservationId={}, userId={}",
                command.getReservationId(), command.getUserId());

        // 1. 분산락 키 생성
        String reservationLockKey = generateReservationLockKey(command.getReservationId());
        String balanceLockKey = generateBalanceLockKey(command.getUserId());
        String lockValue = generateLockValue(command.getUserId());

        // 2. 예약에 대한 분산락 획득 (중복 결제 방지)
        if (!distributedLockService.tryLock(reservationLockKey, lockValue, 10)) {
            throw new IllegalArgumentException("해당 예약에 대한 결제가 이미 진행 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            // 3. 사용자 잔액에 대한 분산락 획득 (동시 잔액 차감 방지)
            if (!distributedLockService.tryLock(balanceLockKey, lockValue, 10)) {
                throw new IllegalArgumentException("사용자의 다른 결제가 진행 중입니다. 잠시 후 다시 시도해주세요.");
            }

            try {
                return processPaymentWithLock(command);
            } finally {
                distributedLockService.unlock(balanceLockKey, lockValue);
            }
        } finally {
            distributedLockService.unlock(reservationLockKey, lockValue);
        }
    }

    /**
     * 분산락 내에서 실행되는 실제 결제 처리 로직
     */
    @Transactional
    protected  PaymentResult processPaymentWithLock(PaymentCommand command) {
        log.info("결제 처리 시작: reservationId={}, userId={}",
                command.getReservationId(), command.getUserId());

        Payment payment = null;
        boolean balanceDeducted = false;

        try {
            // 1. 예약 정보 확인
            Reservation reservation = reservationRepository.findById(command.getReservationId())
                    .orElseThrow(() -> new IllegalArgumentException("예약 정보를 찾을 수 없습니다."));

            validateReservation(reservation, command.getUserId());

            // 2. 결제 금액 설정
            BigDecimal paymentAmount = reservation.getPrice();

            // 3. 잔액 확인
            if (!balanceService.hasEnoughBalance(command.getUserId(), paymentAmount)) {
                BigDecimal currentBalance = balanceService.getBalanceAmount(command.getUserId());
                throw new InsufficientBalanceException(currentBalance.longValue(), paymentAmount.longValue());
            }

            // 4. 결제 정보 생성 (PENDING 상태로 시작)
            payment = new Payment(
                    command.getReservationId(),
                    command.getUserId(),
                    paymentAmount,
                    Payment.PaymentMethod.BALANCE
            );
            paymentRepository.save(payment);

            // 5. 잔액 차감
            Balance updatedBalance = balanceService.deductBalance(
                    command.getUserId(),
                    paymentAmount,
                    command.getReservationId()
            );
            balanceDeducted = true;

            // 6. 결제 완료 처리
            payment.markAsCompleted();
            paymentRepository.save(payment);

            // 7. 예약 확정
            reservation.confirm(LocalDateTime.now());
            reservationRepository.save(reservation);

            // 8. 좌석 확정 (이미 선점된 좌석을 확정으로 변경)
            Seat seat = seatRepository.findById(reservation.getSeatId())
                    .orElseThrow(() -> new IllegalArgumentException("좌석 정보를 찾을 수 없습니다."));

            // 좌석이 해당 사용자에 의해 선점되어 있는지 확인
            if (seat.getStatus() != Seat.SeatStatus.TEMPORARILY_ASSIGNED ||
                    !seat.getAssignedUserId().equals(command.getUserId())) {
                throw new IllegalArgumentException("좌석이 올바르게 선점되지 않았습니다.");
            }

            seat.confirmReservation(LocalDateTime.now());
            seatRepository.save(seat);

            log.info("결제 처리 완료: paymentId={}, amount={}, balanceAfter={}",
                    payment.getPaymentId(), payment.getAmount(), updatedBalance.getAmount());

            return new PaymentResult(payment);

        } catch (Exception e) {
            log.error("결제 처리 중 오류 발생: reservationId={}, userId={}",
                    command.getReservationId(), command.getUserId(), e);

            // 롤백 처리
            if (payment != null && balanceDeducted) {
                rollbackPayment(payment, command.getUserId());
            }

            // 예외 재발생
            if (e instanceof InsufficientBalanceException ||
                    e instanceof PaymentNotFoundException ||
                    e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new RuntimeException("결제 처리 중 오류가 발생했습니다.", e);
        }
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

        try {
            // 1. 결제 정보 조회
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new PaymentNotFoundException(paymentId));

            if (!payment.isCompleted()) {
                throw new IllegalArgumentException("완료된 결제만 환불 가능합니다. 현재 상태: " + payment.getStatus());
            }

            // 2. 환불 금액 추가
            balanceService.refundBalance(payment.getUserId(), payment.getAmount(), reason);

            // 3. 결제 상태 변경 (환불로 변경)
            payment.refund();
            paymentRepository.save(payment);

            // 4. 관련 예약 및 좌석 상태 변경 (선택사항)
            updateRelatedEntitiesForRefund(payment, reason);

            log.info("환불 처리 완료: paymentId={}, amount={}", paymentId, payment.getAmount());

            return new PaymentResult(payment);

        } catch (Exception e) {
            log.error("환불 처리 중 오류 발생: paymentId={}", paymentId, e);
            if (e instanceof PaymentNotFoundException || e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new RuntimeException("환불 처리 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 결제 취소 처리 (결제 전 취소)
     */
    @Transactional
    public PaymentResult cancelPayment(String paymentId, String reason) {
        log.info("결제 취소 처리 시작: paymentId={}, reason={}", paymentId, reason);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (!payment.isPending()) {
            throw new IllegalArgumentException("이미 취소되거나 환불된 결제입니다.");
        }

        payment.cancel();
        paymentRepository.save(payment);

        log.info("결제 취소 완료: paymentId={}", paymentId);

        return new PaymentResult(payment);
    }

    /**
     * 결제 재시도 처리
     */
    @Transactional
    public PaymentResult retryPayment(String paymentId) {
        log.info("결제 재시도 시작: paymentId={}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (!payment.isFailed()) {
            throw new IllegalArgumentException("실패한 결제만 재시도할 수 있습니다. 현재 상태: " + payment.getStatus());
        }

        payment.retry();
        paymentRepository.save(payment);

        // 재시도를 위해 새로운 PaymentCommand 생성하여 다시 처리
        PaymentCommand retryCommand = new PaymentCommand(payment.getReservationId(), payment.getUserId());

        log.info("결제 재시도를 위한 새로운 처리 시작: paymentId={}", paymentId);

        return processPayment(retryCommand);
    }

    /**
     * 예약 유효성 검증
     */
    private void validateReservation(Reservation reservation, String userId) {
        // 이미 로그인한 사용자이고 대기열을 통과해서 예약한 상태이므로
        // 기본적인 검증만 수행
        if (!reservation.getUserId().equals(userId)) {
            throw new IllegalArgumentException("예약자와 결제자가 일치하지 않습니다.");
        }

        if (reservation.isExpired()) {
            throw new IllegalArgumentException("만료된 예약입니다.");
        }

        if (reservation.isConfirmed()) {
            throw new IllegalArgumentException("이미 결제 완료된 예약입니다.");
        }
    }

    /**
     * 결제 롤백 처리
     */
    private void rollbackPayment(Payment payment, String userId) {
        try {
            // 잔액 복구
            balanceService.refundBalance(
                    userId,
                    payment.getAmount(),
                    "결제 처리 실패로 인한 환불"
            );

            // 결제 실패 처리
            payment.markAsFailed();
            paymentRepository.save(payment);

        } catch (Exception rollbackException) {
            log.error("결제 롤백 처리 중 오류 발생", rollbackException);
        }
    }

    /**
     * 분산락 키 생성 메서드들
     */
    private String generateReservationLockKey(String reservationId) {
        return "payment:reservation:" + reservationId;
    }

    private String generateBalanceLockKey(String userId) {
        return "payment:balance:" + userId;
    }

    private String generateLockValue(String userId) {
        return userId + "_" + UUID.randomUUID().toString();
    }

    private void updateRelatedEntitiesForRefund(Payment payment, String reason) {
        try {
            // 예약 상태를 취소로 변경 (선택사항)
            Reservation reservation = reservationRepository.findById(payment.getReservationId())
                    .orElse(null);

            if (reservation != null && reservation.isConfirmed()) {
                reservation.cancel();
                reservationRepository.save(reservation);

                // 좌석 상태는 환불 정책에 따라 처리
                // 현재 Seat 도메인에는 예약된 좌석을 해제하는 메서드가 없음
                // 비즈니스 정책에 따라 좌석을 그대로 두거나 별도 처리 필요
                Seat seat = seatRepository.findById(reservation.getSeatId())
                        .orElse(null);

                if (seat != null && seat.isReserved()) {
                    log.info("환불된 좌석 정보: seatId={}, status={}", seat.getSeatId(), seat.getStatus());
                    // TODO: 환불 시 좌석 처리 정책 결정 필요
                }
            }

        } catch (Exception e) {
            log.warn("환불 시 관련 엔티티 상태 변경 중 오류 발생: paymentId={}", payment.getPaymentId(), e);
            // 환불 자체는 성공했으므로 예외를 던지지 않음
        }
    }
}