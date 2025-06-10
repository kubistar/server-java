package kr.hhplus.be.server.reservation.repository;

import kr.hhplus.be.server.reservation.domain.Reservation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReservationRepositoryImpl implements ReservationRepository {

    private final ReservationJpaRepository reservationJpaRepository;

    @Override
    public Reservation save(Reservation reservation) {
        return reservationJpaRepository.save(reservation);
    }

    @Override
    public Optional<Reservation> findById(String reservationId) {
        return reservationJpaRepository.findById(reservationId);
    }

    @Override
    public List<Reservation> findByUserId(String userId) {
        return reservationJpaRepository.findByUserId(userId);
    }

    @Override
    public List<Reservation> findExpiredReservations() {
        return reservationJpaRepository.findExpiredReservations(LocalDateTime.now());
    }

    @Override
    public void saveAll(List<Reservation> reservations) {
        reservationJpaRepository.saveAll(reservations);
    }
}