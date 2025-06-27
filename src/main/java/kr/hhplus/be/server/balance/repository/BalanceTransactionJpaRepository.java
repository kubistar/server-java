package kr.hhplus.be.server.balance.repository;

import kr.hhplus.be.server.balance.domain.BalanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BalanceTransactionJpaRepository extends JpaRepository<BalanceTransaction, String> {
    List<BalanceTransaction> findByUserIdOrderByCreatedAtDesc(String userId);
}
