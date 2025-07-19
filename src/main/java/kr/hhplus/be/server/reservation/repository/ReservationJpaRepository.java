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

    /**
     * 콘서트별 확정된 예약 수 조회
     *
     * @param concertId 콘서트 ID
     * @param status 예약 상태 (예: "CONFIRMED")
     * @return 해당 콘서트의 확정 예약 수
     */
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.concertId = :concertId AND r.status = :status")
    Long countByConcertIdAndStatus(@Param("concertId") Long concertId, @Param("status") String status);

    /**
     * 모든 콘서트의 예약 수 통계 조회
     *
     * @return [콘서트ID, 예약수] 형태의 데이터 리스트
     */
    @Query("SELECT r.concertId, COUNT(r) FROM Reservation r WHERE r.status = 'CONFIRMED' " +
            "GROUP BY r.concertId ORDER BY COUNT(r) DESC")
    List<Object[]> getReservationCountByConcer();
}