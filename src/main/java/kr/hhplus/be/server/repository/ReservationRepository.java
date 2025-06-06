package kr.hhplus.be.server.repository;

import kr.hhplus.be.server.domain.Reservation;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository {
    Reservation save(Reservation reservation);
    Optional<Reservation> findById(String reservationId);
    List<Reservation> findByUserId(String userId);
    List<Reservation> findExpiredReservations();
    void saveAll(List<Reservation> reservations);
}