package kr.hhplus.be.server.balance.repository;

import kr.hhplus.be.server.balance.domain.BalanceTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class BalanceTransactionRepositoryImpl implements BalanceTransactionRepository {

    private final BalanceTransactionJpaRepository balanceTransactionJpaRepository;

    @Override
    public BalanceTransaction save(BalanceTransaction transaction) {
        return balanceTransactionJpaRepository.save(transaction);
    }

    @Override
    public List<BalanceTransaction> findByUserId(String userId) {
        return balanceTransactionJpaRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}