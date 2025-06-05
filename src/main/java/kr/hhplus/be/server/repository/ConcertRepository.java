package kr.hhplus.be.server.repository;

import kr.hhplus.be.server.domain.Concert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ConcertRepository {

    /**
     * 예약 가능한 콘서트 목록 조회 (현재 날짜 이후)
     */
    Page<Concert> findAvailableConcerts(Pageable pageable);

    /**
     * 콘서트 ID로 조회
     */
    Optional<Concert> findById(Long concertId);

    /**
     * 특정 날짜의 콘서트 목록 조회
     */
    List<Concert> findByConcertDate(LocalDate date);

    /**
     * 아티스트명으로 콘서트 검색
     */
    List<Concert> findByArtistContaining(String artist);

    /**
     * 콘서트 저장
     */
    Concert save(Concert concert);
}