package kr.hhplus.be.server.reservation.repository;

import kr.hhplus.be.server.reservation.domain.Reservation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository {
    Reservation save(Reservation reservation);
    Optional<Reservation> findById(String reservationId);
    List<Reservation> findByUserId(String userId);
    List<Reservation> findExpiredReservations();

    List<Reservation> findByStatusAndExpiresAtBefore(
            Reservation.ReservationStatus status,
            LocalDateTime expiresAt
    );

    void saveAll(List<Reservation> reservations);

    /**
     * 콘서트별 확정된 예약 수 조회
     *
     * @param concertId 콘서트 ID
     * @param status 예약 상태
     * @return 해당 콘서트의 확정 예약 수
     */
    Long countByConcertIdAndStatus(Long concertId, String status);

    /**
     * 모든 콘서트의 예약 수 통계 조회
     *
     * @return [콘서트ID, 예약수] 형태의 데이터 리스트
     */
    List<Object[]> getReservationCountByConcer();
}