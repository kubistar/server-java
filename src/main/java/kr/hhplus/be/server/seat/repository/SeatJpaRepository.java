package kr.hhplus.be.server.seat.repository;

import jakarta.persistence.LockModeType;
import kr.hhplus.be.server.seat.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeatJpaRepository extends JpaRepository<Seat, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.concertId = :concertId AND s.seatNumber = :seatNumber")
    Optional<Seat> findByConcertIdAndSeatNumberWithLock(@Param("concertId") Long concertId,
                                                        @Param("seatNumber") Integer seatNumber);

    List<Seat> findByConcertId(Long concertId);

    List<Seat> findByStatusAndAssignedUntilBefore(
            Seat.SeatStatus status,
            LocalDateTime assignedUntil
    );

    // 콘서트별 전체 좌석 수 카운트
    long countByConcertId(Long concertId);

    // 콘서트별 특정 상태의 좌석 수 카운트
    long countByConcertIdAndStatus(Long concertId, Seat.SeatStatus status);
}
