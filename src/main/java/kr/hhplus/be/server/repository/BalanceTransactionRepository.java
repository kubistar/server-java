package kr.hhplus.be.server.repository;

import kr.hhplus.be.server.domain.BalanceTransaction;
import java.util.List;

public interface BalanceTransactionRepository {
    BalanceTransaction save(BalanceTransaction transaction);
    List<BalanceTransaction> findByUserId(String userId);
}