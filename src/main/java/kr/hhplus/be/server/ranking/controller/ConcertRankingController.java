package kr.hhplus.be.server.ranking.controller;

import kr.hhplus.be.server.ranking.domain.ConcertRanking;
import kr.hhplus.be.server.ranking.domain.RankingType;
import kr.hhplus.be.server.ranking.service.ConcertRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/concerts/ranking")
@RequiredArgsConstructor
public class ConcertRankingController {

    private final ConcertRankingService rankingService;

    /**
     * 콘서트 랭킹 조회
     */
    @GetMapping
    public ResponseEntity<List<ConcertRanking>> getRanking(
            @RequestParam(defaultValue = "POPULARITY") RankingType type,
            @RequestParam(defaultValue = "10") int limit) {

        List<ConcertRanking> rankings = rankingService.getTopRanking(type, limit);
        return ResponseEntity.ok(rankings);
    }

    /**
     * 특정 콘서트의 실시간 예약 속도 조회
     */
    @GetMapping("/{concertId}/booking-speed")
    public ResponseEntity<Double> getBookingSpeed(@PathVariable Long concertId) {
        double speed = rankingService.getRealTimeBookingSpeed(concertId);
        return ResponseEntity.ok(speed);
    }

    /**
     * 매진 속도 랭킹 조회
     */
    @GetMapping("/soldout-speed")
    public ResponseEntity<List<ConcertRanking>> getSoldOutRanking(
            @RequestParam(defaultValue = "10") int limit) {

        List<ConcertRanking> rankings = rankingService.getTopRanking(RankingType.SOLDOUT_SPEED, limit);
        return ResponseEntity.ok(rankings);
    }

    /**
     * 실시간 예약 속도 랭킹 조회
     */
    @GetMapping("/booking-speed")
    public ResponseEntity<List<ConcertRanking>> getBookingSpeedRanking(
            @RequestParam(defaultValue = "10") int limit) {

        List<ConcertRanking> rankings = rankingService.getTopRanking(RankingType.BOOKING_SPEED, limit);
        return ResponseEntity.ok(rankings);
    }

    /**
     * 종합 인기도 랭킹 조회
     */
    @GetMapping("/popularity")
    public ResponseEntity<List<ConcertRanking>> getPopularityRanking(
            @RequestParam(defaultValue = "10") int limit) {

        List<ConcertRanking> rankings = rankingService.getTopRanking(RankingType.POPULARITY, limit);
        return ResponseEntity.ok(rankings);
    }
}