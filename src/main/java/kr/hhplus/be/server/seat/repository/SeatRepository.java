package kr.hhplus.be.server.seat.repository;


import jakarta.persistence.LockModeType;
import kr.hhplus.be.server.seat.domain.Seat;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatRepository {
    Optional<Seat> findById(Long seatId);
    // 비관적 락 메서드 추가
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.concertId = :concertId AND s.seatNumber = :seatNumber")
    Optional<Seat> findByConcertIdAndSeatNumberWithLock(@Param("concertId") Long concertId,
                                                @Param("seatNumber") Integer seatNumber);
    List<Seat> findByConcertId(Long concertId);
    Seat save(Seat seat);
    List<Seat> findExpiredTemporaryAssignments();
    void saveAll(List<Seat> seats);
}
