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
}