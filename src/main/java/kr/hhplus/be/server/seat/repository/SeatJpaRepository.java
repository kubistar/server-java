package kr.hhplus.be.server.seat.repository;

import kr.hhplus.be.server.seat.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeatJpaRepository extends JpaRepository<Seat, Long> {

    @Query("SELECT s FROM Seat s WHERE s.concertId = :concertId AND s.seatNumber = :seatNumber")
    Optional<Seat> findByConcertIdAndSeatNumber(@Param("concertId") Long concertId,
                                                @Param("seatNumber") Integer seatNumber);

    List<Seat> findByConcertId(Long concertId);

    @Query("SELECT s FROM Seat s WHERE s.status = 'TEMPORARILY_ASSIGNED' AND s.assignedUntil < :now")
    List<Seat> findExpiredTemporaryAssignments(@Param("now") LocalDateTime now);
}
