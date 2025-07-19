package kr.hhplus.be.server.ranking.controller;

import kr.hhplus.be.server.ranking.domain.ConcertRanking;
import kr.hhplus.be.server.ranking.domain.RankingType;
import kr.hhplus.be.server.ranking.dto.BookingSpeedResponse;
import kr.hhplus.be.server.ranking.service.ConcertRankingService;
import kr.hhplus.be.server.ranking.service.RankingAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 랭킹 통계 조회 API 컨트롤러
 */
@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
@Slf4j
public class RankingStatisticsController {

    private final RankingAnalyticsService rankingAnalyticsService;
    private final ConcertRankingService concertRankingService;

    /**
     * 콘서트 랭킹 조회
     *
     * @param type 랭킹 타입 (SOLDOUT_SPEED, BOOKING_SPEED, POPULARITY)
     * @param limit 조회할 랭킹 개수 (기본값: 10, 최대: 100)
     * @return 상위 랭킹 목록
     */
    @GetMapping
    public ResponseEntity<List<ConcertRanking>> getRankings(
            @RequestParam(value = "type", defaultValue = "POPULARITY") RankingType type,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {

        log.info("랭킹 조회 요청 - type: {}, limit: {}", type, limit);

        // 제한값 검증
        if (limit > 100) {
            log.error("랭킹 조회 개수 초과 - limit: {}", limit);
            return ResponseEntity.badRequest().build();
        }

        if (limit < 1) {
            log.error("랭킹 조회 개수 부족 - limit: {}", limit);
            return ResponseEntity.badRequest().build();
        }

        try {
            List<ConcertRanking> rankings = concertRankingService.getTopRanking(type, limit);
            log.info("랭킹 조회 성공 - type: {}, count: {}", type, rankings.size());
            return ResponseEntity.ok(rankings);

        } catch (Exception e) {
            log.error("랭킹 조회 실패 - type: {}, limit: {}", type, limit, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 콘서트별 상세 분석 정보 조회
     *
     * @param concertId 콘서트 ID
     * @return 콘서트 상세 분석 데이터
     */
    @GetMapping("/concerts/{concertId}/analytics")
    public ResponseEntity<Map<String, Object>> getConcertAnalytics(@PathVariable Long concertId) {

        log.info("콘서트 분석 정보 조회 요청 - concertId: {}", concertId);

        try {
            Map<String, Object> analytics = rankingAnalyticsService.getConcertRankingAnalytics(concertId);
            log.info("콘서트 분석 정보 조회 성공 - concertId: {}", concertId);
            return ResponseEntity.ok(analytics);

        } catch (Exception e) {
            log.error("콘서트 분석 정보 조회 실패 - concertId: {}", concertId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 예약 트렌드 분석 조회
     *
     * @param concertId 콘서트 ID
     * @param hours 조회 시간 범위 (기본값: 24시간)
     * @return 시간대별 예약 트렌드 데이터
     */
    @GetMapping("/concerts/{concertId}/trends")
    public ResponseEntity<Map<String, Object>> getBookingTrends(
            @PathVariable Long concertId,
            @RequestParam(value = "hours", defaultValue = "24") int hours) {

        log.info("예약 트렌드 조회 요청 - concertId: {}, hours: {}", concertId, hours);

        try {
            Map<String, Object> trends = rankingAnalyticsService.getBookingTrendAnalysis(concertId, hours);
            log.info("예약 트렌드 조회 성공 - concertId: {}, hours: {}", concertId, hours);
            return ResponseEntity.ok(trends);

        } catch (Exception e) {
            log.error("예약 트렌드 조회 실패 - concertId: {}, hours: {}", concertId, hours, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 여러 콘서트 분석 데이터 일괄 조회
     *
     * @param concertIds 콘서트 ID 목록 (쉼표로 구분)
     * @return 콘서트별 분석 데이터
     */
    @GetMapping("/concerts/bulk-analytics")
    public ResponseEntity<Map<Long, Map<String, Object>>> getBulkAnalytics(
            @RequestParam("concertIds") List<Long> concertIds) {

        log.info("다중 콘서트 분석 데이터 조회 요청 - 콘서트 수: {}", concertIds.size());

        try {
            Map<Long, Map<String, Object>> bulkAnalytics =
                    rankingAnalyticsService.getBulkConcertAnalytics(concertIds);
            log.info("다중 콘서트 분석 데이터 조회 성공 - 콘서트 수: {}", concertIds.size());
            return ResponseEntity.ok(bulkAnalytics);

        } catch (IllegalArgumentException e) {
            log.error("다중 콘서트 분석 데이터 조회 실패 - 요청 개수 초과: {}", concertIds.size(), e);
            return ResponseEntity.badRequest().build();

        } catch (Exception e) {
            log.error("다중 콘서트 분석 데이터 조회 실패 - 콘서트 수: {}", concertIds.size(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 실시간 예약 속도 조회
     *
     * @param concertId 콘서트 ID
     * @return 실시간 예약 속도 (분당 예약 수)
     */
    @GetMapping("/concerts/{concertId}/booking-speed")
    public ResponseEntity<BookingSpeedResponse> getBookingSpeed(@PathVariable Long concertId) {

        log.info("실시간 예약 속도 조회 요청 - concertId: {}", concertId);

        try {
            double bookingSpeed = concertRankingService.getRealTimeBookingSpeed(concertId);

            BookingSpeedResponse response = BookingSpeedResponse.builder()
                    .concertId(concertId)
                    .bookingSpeedPerMinute(bookingSpeed)
                    .bookingSpeedPerHour(bookingSpeed * 60)
                    .build();

            log.info("실시간 예약 속도 조회 성공 - concertId: {}, speed: {}/min", concertId, bookingSpeed);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("실시간 예약 속도 조회 실패 - concertId: {}", concertId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 시스템 전체 통계 조회
     *
     * @return 시스템 전체 통계 데이터
     */
    @GetMapping("/system/statistics")
    public ResponseEntity<Map<String, Object>> getSystemStatistics() {

        log.info("시스템 통계 조회 요청");

        try {
            Map<String, Object> statistics = rankingAnalyticsService.getSystemStatistics();
            log.info("시스템 통계 조회 성공");
            return ResponseEntity.ok(statistics);

        } catch (Exception e) {
            log.error("시스템 통계 조회 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 통계 시스템 요약 정보 조회
     *
     * @return 통계 시스템 요약 정보
     */
    @GetMapping("/system/summary")
    public ResponseEntity<Map<String, Object>> getStatisticsSummary() {

        log.info("통계 시스템 요약 정보 조회 요청");

        try {
            Map<String, Object> summary = rankingAnalyticsService.getStatisticsSummary();
            log.info("통계 시스템 요약 정보 조회 성공");
            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            log.error("통계 시스템 요약 정보 조회 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 통계 캐시 상태 조회 (관리자용)
     *
     * @return 캐시 상태 정보
     */
    @GetMapping("/admin/cache-status")
    public ResponseEntity<Map<String, Object>> getCacheStatus() {

        log.info("통계 캐시 상태 조회 요청");

        try {
            Map<String, Object> cacheStatus = rankingAnalyticsService.getStatisticsCacheStatus();
            log.info("통계 캐시 상태 조회 성공");
            return ResponseEntity.ok(cacheStatus);

        } catch (Exception e) {
            log.error("통계 캐시 상태 조회 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 통계 강제 갱신 요청 (관리자용)
     *
     * @param concertId 갱신할 콘서트 ID
     * @return 갱신 요청 결과
     */
    @PostMapping("/admin/concerts/{concertId}/refresh")
    public ResponseEntity<Map<String, Object>> refreshStatistics(@PathVariable Long concertId) {

        log.info("통계 강제 갱신 요청 - concertId: {}", concertId);

        try {
            Map<String, Object> refreshResult = rankingAnalyticsService.requestStatisticsRefresh(concertId);
            log.info("통계 강제 갱신 요청 성공 - concertId: {}", concertId);
            return ResponseEntity.ok(refreshResult);

        } catch (Exception e) {
            log.error("통계 강제 갱신 요청 실패 - concertId: {}", concertId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

}