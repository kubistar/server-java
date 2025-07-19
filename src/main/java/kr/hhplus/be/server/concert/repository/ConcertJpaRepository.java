package kr.hhplus.be.server.concert.repository;


import kr.hhplus.be.server.concert.domain.Concert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ConcertJpaRepository extends JpaRepository<Concert, Long> {

    @Query("SELECT c FROM Concert c WHERE c.concertDate >= :currentDate ORDER BY c.concertDate, c.concertTime")
    Page<Concert> findAvailableConcerts(@Param("currentDate") LocalDate currentDate, Pageable pageable);

    List<Concert> findByConcertDate(LocalDate date);

    List<Concert> findByArtistContaining(String artist);

    /**
     * 매진된 콘서트들 조회
     *
     * @return 매진된 콘서트 목록
     */
    @Query("SELECT c FROM Concert c WHERE c.soldOut = true ORDER BY c.soldOutTime ASC")
    List<Concert> findBySoldOutTrue();

    /**
     * 예약 가능한 콘서트들 조회 (배치용)
     *
     * @return 예약 가능한 콘서트 목록 (매진되지 않고 예약 기간 내)
     */
    @Query("SELECT c FROM Concert c WHERE c.soldOut = false " +
            "AND c.bookingStartTime <= CURRENT_TIMESTAMP " +
            "AND c.bookingEndTime >= CURRENT_TIMESTAMP " +
            "ORDER BY c.bookingStartTime ASC")
    List<Concert> findAvailableConcertsForBatch();
}

