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
}

