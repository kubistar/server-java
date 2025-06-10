package kr.hhplus.be.server.point.repository;

import kr.hhplus.be.server.point.domain.BalanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BalanceTransactionJpaRepository extends JpaRepository<BalanceTransaction, String> {
    List<BalanceTransaction> findByUserIdOrderByCreatedAtDesc(String userId);
}
