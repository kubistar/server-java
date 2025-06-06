package kr.hhplus.be.server.service;

import kr.hhplus.be.server.command.BalanceChargeCommand;
import kr.hhplus.be.server.domain.BalanceTransaction;
import kr.hhplus.be.server.domain.User;
import kr.hhplus.be.server.dto.BalanceResult;
import kr.hhplus.be.server.repository.BalanceTransactionRepository;
import kr.hhplus.be.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceService implements BalanceUseCase {

    private final UserRepository userRepository;
    private final BalanceTransactionRepository balanceTransactionRepository;

    @Override
    @Transactional
    public BalanceResult chargeBalance(BalanceChargeCommand command) {
        log.info("잔액 충전 요청: userId={}, amount={}", command.getUserId(), command.getAmount());

        // 사용자 조회 (비관적 락)
        User user = userRepository.findByIdForUpdate(command.getUserId())
                .orElseGet(() -> new User(command.getUserId(), 0L));

        // 잔액 충전
        Long previousBalance = user.getBalance();
        user.chargeBalance(command.getAmount());

        // 사용자 정보 저장
        User savedUser = userRepository.save(user);

        // 거래 내역 저장
        BalanceTransaction transaction = BalanceTransaction.charge(
                command.getUserId(),
                command.getAmount(),
                savedUser.getBalance()
        );
        balanceTransactionRepository.save(transaction);

        log.info("잔액 충전 완료: userId={}, 이전잔액={}, 충전금액={}, 현재잔액={}",
                command.getUserId(), previousBalance, command.getAmount(), savedUser.getBalance());

        return new BalanceResult(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceResult getBalance(String userId) {
        log.debug("잔액 조회: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseGet(() -> new User(userId, 0L));

        return new BalanceResult(user);
    }
}