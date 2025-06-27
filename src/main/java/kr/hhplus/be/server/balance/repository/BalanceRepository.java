package kr.hhplus.be.server.balance.repository;

import kr.hhplus.be.server.balance.domain.Balance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface BalanceRepository extends JpaRepository<Balance, String> {

    /**
     * 사용자 ID로 잔액 조회
     */
    Optional<Balance> findByUserId(String userId);

    /**
     * 비관적 락을 사용한 잔액 조회 (동시성 제어)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Balance b WHERE b.userId = :userId")
    Optional<Balance> findByUserIdWithLock(@Param("userId") String userId);

    /**
     * 사용자 잔액 존재 여부 확인
     */
    boolean existsByUserId(String userId);

    /**
     * 특정 금액 이상의 잔액을 가진 사용자 수 조회
     */
    @Query("SELECT COUNT(b) FROM Balance b WHERE b.amount >= :minAmount")
    long countByAmountGreaterThanEqual(@Param("minAmount") BigDecimal minAmount);

    /**
     * 잔액 직접 업데이트 (낙관적 락 대안)
     */
    @Modifying
    @Query("UPDATE Balance b SET b.amount = b.amount + :amount, b.updatedAt = CURRENT_TIMESTAMP WHERE b.userId = :userId")
    int addAmountByUserId(@Param("userId") String userId, @Param("amount") BigDecimal amount);

    /**
     * 잔액 차감 (충분한 잔액이 있는 경우에만)
     */
    @Modifying
    @Query("UPDATE Balance b SET b.amount = b.amount - :amount, b.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE b.userId = :userId AND b.amount >= :amount")
    int deductAmountByUserId(@Param("userId") String userId, @Param("amount") BigDecimal amount);

    /**
     * 사용자 잔액이 특정 금액 이상인지 확인
     */
    @Query("SELECT CASE WHEN b.amount >= :requiredAmount THEN true ELSE false END " +
            "FROM Balance b WHERE b.userId = :userId")
    Optional<Boolean> hasEnoughAmount(@Param("userId") String userId, @Param("requiredAmount") BigDecimal requiredAmount);

    /**
     * 모든 사용자의 총 잔액 합계
     */
    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Balance b")
    BigDecimal getTotalBalance();

    /**
     * 잔액이 0원인 사용자들 조회
     */
    @Query("SELECT b FROM Balance b WHERE b.amount = 0")
    java.util.List<Balance> findZeroBalanceUsers();

    /**
     * 사용자 잔액 조회 (없으면 0 반환)
     */
    @Query("SELECT COALESCE(b.amount, 0) FROM Balance b WHERE b.userId = :userId")
    BigDecimal getAmountByUserId(@Param("userId") String userId);
}