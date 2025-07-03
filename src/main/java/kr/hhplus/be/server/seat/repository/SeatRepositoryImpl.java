package kr.hhplus.be.server.seat.repository;

import kr.hhplus.be.server.seat.domain.Seat;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class SeatRepositoryImpl implements SeatRepository {

    private final SeatJpaRepository seatJpaRepository;

    public SeatRepositoryImpl(SeatJpaRepository seatJpaRepository) {
        this.seatJpaRepository = seatJpaRepository;
    }

    @Override
    public Optional<Seat> findById(Long seatId) {
        return seatJpaRepository.findById(seatId);
    }

    @Override
    public Optional<Seat> findByConcertIdAndSeatNumberWithLock(Long concertId, Integer seatNumber) {
        return seatJpaRepository.findByConcertIdAndSeatNumber(concertId, seatNumber);
    }

    @Override
    public List<Seat> findByConcertId(Long concertId) {
        return seatJpaRepository.findByConcertId(concertId);
    }

    @Override
    public Seat save(Seat seat) {
        return seatJpaRepository.save(seat);
    }

    @Override
    public List<Seat> findExpiredTemporaryAssignments() {
        return seatJpaRepository.findByStatusAndAssignedUntilBefore(
                Seat.SeatStatus.TEMPORARILY_ASSIGNED, LocalDateTime.now()
        );
    }

    @Override
    public void saveAll(List<Seat> seats) {
        seatJpaRepository.saveAll(seats);
    }
}
