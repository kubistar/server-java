package kr.hhplus.be.server.user.repository;

import kr.hhplus.be.server.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") String userId);

    @Modifying
    @Query("UPDATE User u SET u.balance = u.balance - :amount " +
            "WHERE u.userId = :userId AND u.balance >= :amount")
    int deductBalanceWithCondition(@Param("userId") String userId, @Param("amount") Long amount);
}
