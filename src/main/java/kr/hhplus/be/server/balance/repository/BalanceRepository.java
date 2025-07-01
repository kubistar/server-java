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
     * 사용자 ID로 잔액 정보를 조회
     *
     * @param userId 조회할 사용자 ID
     * @return 잔액 정보 (존재하지 않으면 Optional.empty())
     */
    Optional<Balance> findByUserId(String userId);

    /**
     * 비관적 락을 사용하여 사용자 잔액 정보를 조회
     * 동시성 제어가 필요한 상황에서 사용
     *
     * @param userId 조회할 사용자 ID
     * @return 비관적 락이 적용된 잔액 정보 (존재하지 않으면 Optional.empty())
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Balance b WHERE b.userId = :userId")
    Optional<Balance> findByUserIdWithLock(@Param("userId") String userId);

    /**
     * 사용자 잔액 존재 여부를 확인
     *
     * @param userId 확인할 사용자 ID
     * @return 잔액이 존재하면 true, 존재하지 않으면 false
     */
    boolean existsByUserId(String userId);

    /**
     * 특정 금액 이상의 잔액을 가진 사용자 수를 조회
     *
     * @param minAmount 최소 잔액 기준
     * @return 조건에 해당하는 사용자 수
     */
    @Query("SELECT COUNT(b) FROM Balance b WHERE b.amount >= :minAmount")
    long countByAmountGreaterThanEqual(@Param("minAmount") BigDecimal minAmount);

    /**
     * 사용자 잔액에 금액을 추가
     * 업데이트 시간도 함께 현재 시간으로 갱신
     *
     * @param userId 잔액을 업데이트할 사용자 ID
     * @param amount 추가할 금액 (양수/음수 모두 가능)
     * @return 업데이트된 레코드 수 (성공 시 1, 실패 시 0)
     */
    @Modifying
    @Query("UPDATE Balance b SET b.amount = b.amount + :amount, b.updatedAt = CURRENT_TIMESTAMP WHERE b.userId = :userId")
    int addAmountByUserId(@Param("userId") String userId, @Param("amount") BigDecimal amount);

    /**
     * 사용자 잔액에서 금액을 차감
     * 충분한 잔액이 있는 경우에만 차감이 실행되며, 업데이트 시간도 함께 갱신
     *
     * @param userId 잔액을 차감할 사용자 ID
     * @param amount 차감할 금액
     * @return 업데이트된 레코드 수 (성공 시 1, 잔액 부족 시 0)
     */
    @Modifying
    @Query("UPDATE Balance b SET b.amount = b.amount - :amount, b.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE b.userId = :userId AND b.amount >= :amount")
    int deductAmountByUserId(@Param("userId") String userId, @Param("amount") BigDecimal amount);

    /**
     * 사용자의 잔액이 특정 금액 이상인지 확인
     *
     * @param userId 확인할 사용자 ID
     * @param requiredAmount 필요한 최소 금액
     * @return 충분한 잔액이 있으면 true, 부족하면 false (사용자가 존재하지 않으면 Optional.empty())
     */
    @Query("SELECT CASE WHEN b.amount >= :requiredAmount THEN true ELSE false END " +
            "FROM Balance b WHERE b.userId = :userId")
    Optional<Boolean> hasEnoughAmount(@Param("userId") String userId, @Param("requiredAmount") BigDecimal requiredAmount);

    /**
     * 모든 사용자의 총 잔액 합계를 조회
     *
     * @return 전체 사용자의 잔액 합계 (데이터가 없으면 0)
     */
    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Balance b")
    BigDecimal getTotalBalance();

    /**
     * 잔액이 0원인 모든 사용자를 조회
     *
     * @return 잔액이 0원인 사용자들의 Balance 엔티티 리스트
     */
    @Query("SELECT b FROM Balance b WHERE b.amount = 0")
    java.util.List<Balance> findZeroBalanceUsers();

    /**
     * 사용자의 잔액 금액만 조회
     * 사용자가 존재하지 않는 경우 0을 반환합니다.
     *
     * @param userId 조회할 사용자 ID
     * @return 사용자의 잔액 (존재하지 않으면 0)
     */
    @Query("SELECT COALESCE(b.amount, 0) FROM Balance b WHERE b.userId = :userId")
    BigDecimal getAmountByUserId(@Param("userId") String userId);
}