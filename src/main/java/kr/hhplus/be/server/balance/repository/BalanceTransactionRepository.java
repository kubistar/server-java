package kr.hhplus.be.server.balance.repository;

import kr.hhplus.be.server.balance.domain.BalanceTransaction;
import java.util.List;

public interface BalanceTransactionRepository {
    BalanceTransaction save(BalanceTransaction transaction);
    List<BalanceTransaction> findByUserId(String userId);
}