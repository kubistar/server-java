package kr.hhplus.be.server.balance.service;

import kr.hhplus.be.server.balance.command.BalanceChargeCommand;
import kr.hhplus.be.server.balance.domain.Balance;
import kr.hhplus.be.server.balance.domain.BalanceTransaction;
import kr.hhplus.be.server.balance.event.BalanceChargedEvent;
import kr.hhplus.be.server.balance.event.BalanceDeductedEvent;
import kr.hhplus.be.server.balance.repository.BalanceRepository;
import kr.hhplus.be.server.balance.repository.BalanceTransactionRepository;
import kr.hhplus.be.server.balance.dto.BalanceResult;
import kr.hhplus.be.server.common.event.EventPublisher;
import kr.hhplus.be.server.common.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BalanceServiceWithEvents implements BalanceUseCase {

    private final BalanceRepository balanceRepository;
    private final BalanceTransactionRepository balanceTransactionRepository;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public BalanceResult chargeBalance(BalanceChargeCommand command) {
        log.info("잔액 충전 요청: userId={}, amount={}", command.getUserId(), command.getAmount());

        // 1. 비관적 락으로 잔액 조회 또는 생성
        Balance balance = balanceRepository.findByUserIdWithLock(command.getUserId())
                .orElse(new Balance(command.getUserId(), BigDecimal.ZERO));

        BigDecimal previousAmount = balance.getAmount();

        // 2. 잔액 충전
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
        BalanceTransaction savedTransaction = balanceTransactionRepository.save(transaction);

        // 5. ✨ 잔액 충전 이벤트 발행
        BalanceChargedEvent event = new BalanceChargedEvent(
                command.getUserId(),
                chargeAmount,
                savedBalance.getAmount(),
                savedTransaction.getTransactionId()
        );
        eventPublisher.publishEvent(KafkaTopics.BALANCE_EVENTS, command.getUserId(), event);

        log.info("잔액 충전 완료 및 이벤트 발행: userId={}, 이전잔액={}, 충전금액={}, 현재잔액={}",
                command.getUserId(), previousAmount, chargeAmount, savedBalance.getAmount());

        return new BalanceResult(savedBalance);
    }

    @Transactional
    public Balance deductBalance(String userId, BigDecimal amount, String reservationId) {
        log.info("잔액 차감 요청: userId={}, amount={}, reservationId={}", userId, amount, reservationId);

        // 1. 비관적 락으로 잔액 조회
        Balance balance = balanceRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 잔액 정보가 없습니다: " + userId));

        BigDecimal previousAmount = balance.getAmount();

        // 2. 잔액 차감
        balance.deductAmount(amount);

        // 3. 잔액 저장
        Balance savedBalance = balanceRepository.save(balance);

        // 4. 거래 내역 기록
        BalanceTransaction transaction = BalanceTransaction.payment(
                userId,
                amount,
                savedBalance.getAmount(),
                reservationId
        );
        BalanceTransaction savedTransaction = balanceTransactionRepository.save(transaction);

        // 5. ✨ 잔액 차감 이벤트 발행
        BalanceDeductedEvent event = new BalanceDeductedEvent(
                userId,
                amount,
                savedBalance.getAmount(),
                reservationId,
                savedTransaction.getTransactionId()
        );
        eventPublisher.publishEvent(KafkaTopics.BALANCE_EVENTS, userId, event);

        log.info("잔액 차감 완료 및 이벤트 발행: userId={}, 이전잔액={}, 차감금액={}, 현재잔액={}",
                userId, previousAmount, amount, savedBalance.getAmount());

        return savedBalance;
    }

    @Override
    public BalanceResult getBalance(String userId) {
        log.debug("잔액 조회: userId={}", userId);

        Balance balance = balanceRepository.findByUserId(userId)
                .orElse(new Balance(userId, BigDecimal.ZERO));

        return new BalanceResult(balance);
    }
}