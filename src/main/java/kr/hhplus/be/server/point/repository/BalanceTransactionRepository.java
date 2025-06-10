package kr.hhplus.be.server.point.repository;

import kr.hhplus.be.server.point.domain.BalanceTransaction;
import java.util.List;

public interface BalanceTransactionRepository {
    BalanceTransaction save(BalanceTransaction transaction);
    List<BalanceTransaction> findByUserId(String userId);
}