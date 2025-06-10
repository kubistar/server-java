package kr.hhplus.be.server.seat.repository;


import kr.hhplus.be.server.seat.domain.Seat;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository {
    Optional<Seat> findById(Long seatId);
    Optional<Seat> findByConcertIdAndSeatNumber(Long concertId, Integer seatNumber);
    List<Seat> findByConcertId(Long concertId);
    Seat save(Seat seat);
    List<Seat> findExpiredTemporaryAssignments();
    void saveAll(List<Seat> seats);
}
