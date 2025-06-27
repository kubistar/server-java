package kr.hhplus.be.server.balance.service;

import kr.hhplus.be.server.balance.command.BalanceChargeCommand;
import kr.hhplus.be.server.balance.domain.Balance;
import kr.hhplus.be.server.balance.domain.BalanceTransaction;
import kr.hhplus.be.server.balance.repository.BalanceRepository;
import kr.hhplus.be.server.balance.repository.BalanceTransactionRepository;
import kr.hhplus.be.server.balance.dto.BalanceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BalanceService implements BalanceUseCase {

    private final BalanceRepository balanceRepository;
    private final BalanceTransactionRepository balanceTransactionRepository;

    @Override
    @Transactional
    public BalanceResult chargeBalance(BalanceChargeCommand command) {
        log.info("잔액 충전 요청: userId={}, amount={}", command.getUserId(), command.getAmount());

        // 1. 비관적 락으로 잔액 조회 또는 생성
        Balance balance = balanceRepository.findByUserIdWithLock(command.getUserId())
                .orElse(new Balance(command.getUserId(), BigDecimal.ZERO));

        BigDecimal previousAmount = balance.getAmount();

        // 2. 잔액 충전 (Long → BigDecimal 변환)
        BigDecimal chargeAmount = BigDecimal.valueOf(command.getAmount());
        balance.chargeAmount(chargeAmount);

        // 3. 잔액 저장
        Balance savedBalance = balanceRepository.save(balance);

        // 4. 거래 내역 기록
        BalanceTransaction transaction = BalanceTransaction.charge(
                command.getUserId(),
                chargeAmount,
                savedBalance.getAmount()
        );
        balanceTransactionRepository.save(transaction);

        log.info("잔액 충전 완료: userId={}, 이전잔액={}, 충전금액={}, 현재잔액={}",
                command.getUserId(), previousAmount, chargeAmount, savedBalance.getAmount());

        return new BalanceResult(savedBalance);
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceResult getBalance(String userId) {
        log.debug("잔액 조회: userId={}", userId);

        Balance balance = balanceRepository.findByUserId(userId)
                .orElse(new Balance(userId, BigDecimal.ZERO));

        return new BalanceResult(balance);
    }

    /**
     * 사용자 잔액 조회 (BigDecimal 반환) - Repository 메서드 활용
     */
    public BigDecimal getBalanceAmount(String userId) {
        log.debug("잔액 금액 조회: userId={}", userId);

        // Repository의 getAmountByUserId 메서드 활용
        return balanceRepository.getAmountByUserId(userId);
    }

    /**
     * 잔액 충분 여부 확인 - Repository 메서드 활용
     */
    public boolean hasEnoughBalance(String userId, BigDecimal requiredAmount) {
        log.debug("잔액 충분 여부 확인: userId={}, requiredAmount={}", userId, requiredAmount);

        // Repository의 hasEnoughAmount 메서드 활용
        boolean hasEnough = balanceRepository.hasEnoughAmount(userId, requiredAmount)
                .orElse(false);

        log.debug("잔액 확인 결과: requiredAmount={}, hasEnough={}", requiredAmount, hasEnough);

        return hasEnough;
    }

    /**
     * 잔액 차감 (결제용) - 비관적 락 사용
     */
    @Transactional
    public Balance deductBalance(String userId, BigDecimal amount, String reservationId) {
        log.info("잔액 차감 요청: userId={}, amount={}, reservationId={}", userId, amount, reservationId);

        // 1. 비관적 락으로 잔액 조회
        Balance balance = balanceRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 잔액 정보가 없습니다: " + userId));

        BigDecimal previousAmount = balance.getAmount();

        // 2. 잔액 충분성 확인
        if (balance.getAmount().compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    String.format("잔액이 부족합니다. 현재 잔액: %s원, 필요 금액: %s원",
                            balance.getAmount(), amount)
            );
        }

        // 3. 잔액 차감
        balance.deductAmount(amount);

        // 4. 잔액 저장
        Balance savedBalance = balanceRepository.save(balance);

        // 5. 거래 내역 기록
        BalanceTransaction transaction = BalanceTransaction.payment(
                userId,
                amount,
                savedBalance.getAmount(),
                reservationId
        );
        balanceTransactionRepository.save(transaction);

        log.info("잔액 차감 완료: userId={}, 이전잔액={}, 차감금액={}, 현재잔액={}",
                userId, previousAmount, amount, savedBalance.getAmount());

        return savedBalance;
    }

    /**
     * 잔액 차감 (Repository의 원자적 업데이트 메서드 활용) - 대안 방식
     */
    @Transactional
    public boolean deductBalanceWithCondition(String userId, BigDecimal amount, String reservationId) {
        log.info("원자적 잔액 차감 요청: userId={}, amount={}, reservationId={}", userId, amount, reservationId);

        // Repository의 deductAmountByUserId 메서드 활용 (원자적 업데이트)
        int updatedRows = balanceRepository.deductAmountByUserId(userId, amount);

        if (updatedRows > 0) {
            // 차감 성공 시 거래 내역 기록
            BigDecimal balanceAfter = balanceRepository.getAmountByUserId(userId);

            BalanceTransaction transaction = BalanceTransaction.payment(
                    userId,
                    amount,
                    balanceAfter,
                    reservationId
            );
            balanceTransactionRepository.save(transaction);

            log.info("원자적 잔액 차감 완료: userId={}, 차감금액={}, 현재잔액={}",
                    userId, amount, balanceAfter);

            return true;
        } else {
            log.warn("잔액 차감 실패 (잔액 부족 또는 사용자 없음): userId={}, amount={}", userId, amount);
            return false;
        }
    }

    /**
     * 환불 처리
     */
    @Transactional
    public Balance refundBalance(String userId, BigDecimal amount, String reason) {
        log.info("환불 처리 요청: userId={}, amount={}, reason={}", userId, amount, reason);

        // 1. 비관적 락으로 잔액 조회 또는 생성
        Balance balance = balanceRepository.findByUserIdWithLock(userId)
                .orElse(new Balance(userId, BigDecimal.ZERO));

        BigDecimal previousAmount = balance.getAmount();

        // 2. 환불 금액 추가
        balance.chargeAmount(amount);

        // 3. 잔액 저장
        Balance savedBalance = balanceRepository.save(balance);

        // 4. 거래 내역 기록
        BalanceTransaction transaction = BalanceTransaction.refund(
                userId,
                amount,
                savedBalance.getAmount(),
                reason
        );
        balanceTransactionRepository.save(transaction);

        log.info("환불 처리 완료: userId={}, 이전잔액={}, 환불금액={}, 현재잔액={}",
                userId, previousAmount, amount, savedBalance.getAmount());

        return savedBalance;
    }

    /**
     * 환불 처리 (Repository의 원자적 업데이트 메서드 활용) - 대안 방식
     */
    @Transactional
    public boolean refundBalanceWithAdd(String userId, BigDecimal amount, String reason) {
        log.info("원자적 환불 처리 요청: userId={}, amount={}, reason={}", userId, amount, reason);

        // Repository의 addAmountByUserId 메서드 활용
        int updatedRows = balanceRepository.addAmountByUserId(userId, amount);

        if (updatedRows > 0) {
            // 환불 성공 시 거래 내역 기록
            BigDecimal balanceAfter = balanceRepository.getAmountByUserId(userId);

            BalanceTransaction transaction = BalanceTransaction.refund(
                    userId,
                    amount,
                    balanceAfter,
                    reason
            );
            balanceTransactionRepository.save(transaction);

            log.info("원자적 환불 처리 완료: userId={}, 환불금액={}, 현재잔액={}",
                    userId, amount, balanceAfter);

            return true;
        } else {
            log.warn("환불 처리 실패 (사용자 없음): userId={}, amount={}", userId, amount);
            return false;
        }
    }

    /**
     * 사용자 잔액 정보 조회 (엔티티)
     */
    public Balance getBalanceEntity(String userId) {
        return balanceRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 잔액 정보가 없습니다: " + userId));
    }

    /**
     * 사용자 잔액 계정 생성 (회원가입 시)
     */
    @Transactional
    public Balance createBalanceAccount(String userId) {
        if (balanceRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("이미 잔액 계정이 존재합니다: " + userId);
        }

        Balance balance = new Balance(userId, BigDecimal.ZERO);
        return balanceRepository.save(balance);
    }

    /**
     * 잔액 계정 존재 여부 확인
     */
    public boolean existsBalanceAccount(String userId) {
        return balanceRepository.existsByUserId(userId);
    }

    /**
     * 전체 사용자 잔액 합계 조회 - Repository 메서드 활용
     */
    public BigDecimal getTotalBalance() {
        return balanceRepository.getTotalBalance();
    }

    /**
     * 특정 금액 이상 잔액 보유 사용자 수 조회 - Repository 메서드 활용
     */
    public long countUsersWithMinBalance(BigDecimal minAmount) {
        return balanceRepository.countByAmountGreaterThanEqual(minAmount);
    }
}