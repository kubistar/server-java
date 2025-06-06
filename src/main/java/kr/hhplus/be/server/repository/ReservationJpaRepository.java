package kr.hhplus.be.server.repository;

import kr.hhplus.be.server.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationJpaRepository extends JpaRepository<Reservation, String> {

    List<Reservation> findByUserId(String userId);

    @Query("SELECT r FROM Reservation r WHERE r.status = 'TEMPORARILY_ASSIGNED' AND r.expiresAt < :now")
    List<Reservation> findExpiredReservations(@Param("now") LocalDateTime now);
}