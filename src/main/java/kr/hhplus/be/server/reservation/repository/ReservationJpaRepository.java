package kr.hhplus.be.server.reservation.repository;

import kr.hhplus.be.server.reservation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationJpaRepository extends JpaRepository<Reservation, String> {

    List<Reservation> findByUserId(String userId);

    List<Reservation> findByStatusAndExpiresAtBefore(
            Reservation.ReservationStatus status,
            LocalDateTime expiresAt
    );
}