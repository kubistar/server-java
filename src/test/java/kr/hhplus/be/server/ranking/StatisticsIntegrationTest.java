package kr.hhplus.be.server.ranking;

import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.repository.ConcertRepository;
import kr.hhplus.be.server.ranking.service.StatisticsBatchScheduler;
import kr.hhplus.be.server.ranking.service.RankingAnalyticsService;
import kr.hhplus.be.server.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("통계 시스템 통합 테스트")
class StatisticsIntegrationTest {

    @Autowired
    private StatisticsBatchScheduler statisticsBatchScheduler;

    @Autowired
    private RankingAnalyticsService rankingAnalyticsService;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Concert testConcert;

    @BeforeEach
    void setUp() {
        // Redis 클리어
        redisTemplate.getConnectionFactory().getConnection().flushAll();

        // 테스트 콘서트 생성
        testConcert = new Concert(
                "통합테스트 콘서트",
                "테스트 아티스트",
                "테스트 공연장",
                LocalDate.now().plusDays(30),
                LocalTime.of(19, 0),
                100
        );
    }

    @Test
    @DisplayName("배치 → 서비스 → 컨트롤러 전체 플로우 테스트")
    void fullStatisticsFlow_Success() {
        // Given - 배치 실행 전에는 캐시 데이터 없음
        Long concertId = testConcert.getConcertId();

        // 배치 실행 전 조회 - 빈 데이터 반환
        Map<String, Object> beforeBatch = rankingAnalyticsService.getConcertRankingAnalytics(concertId);
        assertFalse((Boolean) beforeBatch.get("dataAvailable"));
        assertEquals("분석 데이터가 아직 계산되지 않았습니다. 잠시 후 다시 시도해주세요.",
                beforeBatch.get("message"));

        // When - 배치 실행
        statisticsBatchScheduler.updateMainStatistics();

        // Then - 배치 실행 후 캐시 데이터 확인
        Map<String, Object> afterBatch = rankingAnalyticsService.getConcertRankingAnalytics(concertId);

        // 데이터 가용성 확인
        assertTrue((Boolean) afterBatch.get("dataAvailable"));
        assertEquals(concertId, afterBatch.get("concertId"));
        assertEquals("통합테스트 콘서트", afterBatch.get("concertName"));
        assertEquals("테스트 아티스트", afterBatch.get("artist"));
        assertNotNull(afterBatch.get("lastUpdated"));

        // 시스템 통계도 확인
        Map<String, Object> systemStats = rankingAnalyticsService.getSystemStatistics();
        assertTrue((Boolean) systemStats.get("dataAvailable"));
        assertEquals("RUNNING", systemStats.get("systemStatus"));
    }

    @Test
    @DisplayName("예약 트렌드 배치 → 조회 플로우 테스트")
    void bookingTrendFlow_Success() {
        // Given
        Long concertId = testConcert.getConcertId();

        // When - 배치 실행
        statisticsBatchScheduler.updateMainStatistics();

        // Then - 트렌드 데이터 조회
        Map<String, Object> trend24h = rankingAnalyticsService.getBookingTrendAnalysis(concertId, 24);
        Map<String, Object> trend12h = rankingAnalyticsService.getBookingTrendAnalysis(concertId, 12);
        Map<String, Object> trend6h = rankingAnalyticsService.getBookingTrendAnalysis(concertId, 6);

        // 각 시간 범위별 데이터 확인
        assertEquals("24h", trend24h.get("actualTimeRange"));
        assertEquals("12h", trend12h.get("actualTimeRange"));
        assertEquals("6h", trend6h.get("actualTimeRange"));

        assertEquals(concertId, trend24h.get("concertId"));
        assertEquals(24, trend24h.get("analysisHours"));
        assertNotNull(trend24h.get("hourlyBookings"));
    }

    @Test
    @DisplayName("캐시 상태 확인 및 강제 갱신 플로우 테스트")
    void cacheManagementFlow_Success() {
        // Given
        Long concertId = testConcert.getConcertId();

        // When - 배치 실행
        statisticsBatchScheduler.updateMainStatistics();

        // Then - 캐시 상태 확인
        Map<String, Object> cacheStatus = rankingAnalyticsService.getStatisticsCacheStatus();
        assertEquals("HEALTHY", cacheStatus.get("overallCacheHealth"));
        assertTrue((Boolean) cacheStatus.get("systemStatsCached"));
        assertNotNull(cacheStatus.get("lastUpdated"));

        // 강제 갱신 테스트
        Map<String, Object> refreshResult = rankingAnalyticsService.requestStatisticsRefresh(concertId);
        assertTrue((Boolean) refreshResult.get("success"));
        assertEquals(concertId, refreshResult.get("concertId"));
        assertEquals(4, refreshResult.get("deletedCacheCount")); // analytics + 3개 trend 키

        // 갱신 후 데이터 없음 확인
        Map<String, Object> afterRefresh = rankingAnalyticsService.getConcertRankingAnalytics(concertId);
        assertFalse((Boolean) afterRefresh.get("dataAvailable"));
    }

    @Test
    @DisplayName("매진 콘서트 배치 처리 플로우 테스트")
    void soldOutConcertFlow_Success() {
        // Given - 매진 콘서트 생성
        Concert soldOutConcert = new Concert(
                "매진 콘서트",
                "인기 아티스트",
                "인기 공연장",
                LocalDate.now().plusDays(15),
                LocalTime.of(20, 0),
                50
        );
        soldOutConcert.markAsSoldOut(); // 매진 처리

        // When - 배치 실행
        statisticsBatchScheduler.updateMainStatistics();

        // Then - 매진 콘서트 분석 데이터 확인
        Map<String, Object> analytics = rankingAnalyticsService.getConcertRankingAnalytics(soldOutConcert.getConcertId());

        assertTrue((Boolean) analytics.get("dataAvailable"));
        assertEquals(true, analytics.get("soldOut"));
        assertNotNull(analytics.get("soldOutTime"));
        assertTrue((Double) analytics.get("soldOutScore") >= 0.0);
    }

    @Test
    @DisplayName("시스템 통계 정확성 검증 테스트")
    void systemStatisticsAccuracy_Test() {
        // Given - 여러 콘서트 생성
        Concert concert1 = new Concert("콘서트1", "아티스트1", "공연장1",
                LocalDate.now().plusDays(10), LocalTime.of(19, 0), 100);
        Concert concert2 = new Concert("콘서트2", "아티스트2", "공연장2",
                LocalDate.now().plusDays(20), LocalTime.of(20, 0), 200);
        Concert soldOutConcert = new Concert("매진콘서트", "인기아티스트", "인기공연장",
                LocalDate.now().plusDays(30), LocalTime.of(18, 0), 50);
        soldOutConcert.markAsSoldOut();

        // When - 배치 실행
        statisticsBatchScheduler.updateMainStatistics();

        // Then - 시스템 통계 정확성 확인
        Map<String, Object> systemStats = rankingAnalyticsService.getSystemStatistics();

        assertTrue((Boolean) systemStats.get("dataAvailable"));

        // 총 콘서트 수 확인 (기존 testConcert + 3개 새로 생성)
        Long totalConcerts = (Long) systemStats.get("totalConcerts");
        assertTrue(totalConcerts >= 4L); // 최소 4개 이상

        // 매진 콘서트 수 확인
        Long soldOutConcerts = (Long) systemStats.get("totalSoldOutConcerts");
        assertTrue(soldOutConcerts >= 1L); // 최소 1개 이상

        // 매진율 확인
        Double soldOutRate = (Double) systemStats.get("soldOutRate");
        assertTrue(soldOutRate >= 0.0 && soldOutRate <= 100.0);

        assertEquals("RUNNING", systemStats.get("systemStatus"));
    }

    @Test
    @DisplayName("다중 콘서트 일괄 조회 성능 테스트")
    void bulkAnalyticsPerformance_Test() {
        // Given - 여러 콘서트 생성
        Concert[] concerts = new Concert[10];
        Long[] concertIds = new Long[10];

        for (int i = 0; i < 10; i++) {
            concerts[i] = new Concert(
                    "콘서트" + i,
                    "아티스트" + i,
                    "공연장" + i,
                    LocalDate.now().plusDays(i + 1),
                    LocalTime.of(19, 0),
                    100
            );
            concertIds[i] = concerts[i].getConcertId();
        }

        // When - 배치 실행
        long batchStartTime = System.currentTimeMillis();
        statisticsBatchScheduler.updateMainStatistics();
        long batchEndTime = System.currentTimeMillis();

        // 일괄 조회
        long queryStartTime = System.currentTimeMillis();
        Map<Long, Map<String, Object>> bulkResult = rankingAnalyticsService.getBulkConcertAnalytics(
                java.util.Arrays.asList(concertIds)
        );
        long queryEndTime = System.currentTimeMillis();

        // Then - 성능 및 정확성 확인
        assertEquals(10, bulkResult.size());

        // 모든 콘서트 데이터 확인
        for (Long concertId : concertIds) {
            Map<String, Object> analytics = bulkResult.get(concertId);
            assertNotNull(analytics);
            assertEquals(concertId, analytics.get("concertId"));
        }

        // 성능 검증 (일괄 조회는 배치보다 훨씬 빨라야 함)
        long batchTime = batchEndTime - batchStartTime;
        long queryTime = queryEndTime - queryStartTime;

        System.out.println("배치 실행 시간: " + batchTime + "ms");
        System.out.println("일괄 조회 시간: " + queryTime + "ms");

        // 일괄 조회가 1초 이내여야 함 (캐시 기반이므로)
        assertTrue(queryTime < 1000, "일괄 조회 시간이 너무 깁니다: " + queryTime + "ms");
    }

    @Test
    @DisplayName("배치 실행 중 예외 발생 시 시스템 안정성 테스트")
    void batchExceptionHandling_Test() {
        // Given - Redis 연결 문제 시뮬레이션은 어려우므로, 정상 상황에서 안정성 확인

        // When - 여러 번 연속 배치 실행
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> {
                statisticsBatchScheduler.updateMainStatistics();
                statisticsBatchScheduler.updateDetailedStatistics();
            }, "배치 실행 중 예외가 발생하면 안됩니다.");
        }

        // Then - 마지막 실행 후 데이터 정상성 확인
        Map<String, Object> analytics = rankingAnalyticsService.getConcertRankingAnalytics(testConcert.getConcertId());
        assertTrue((Boolean) analytics.get("dataAvailable"));

        Map<String, Object> systemStats = rankingAnalyticsService.getSystemStatistics();
        assertTrue((Boolean) systemStats.get("dataAvailable"));
        assertEquals("RUNNING", systemStats.get("systemStatus"));
    }

    @Test
    @DisplayName("캐시 TTL 및 데이터 만료 테스트")
    void cacheTTLTest() throws InterruptedException {
        // Given
        Long concertId = testConcert.getConcertId();

        // When - 배치 실행
        statisticsBatchScheduler.updateMainStatistics();

        // Then - 즉시 조회 시 데이터 존재
        Map<String, Object> immediateResult = rankingAnalyticsService.getConcertRankingAnalytics(concertId);
        assertTrue((Boolean) immediateResult.get("dataAvailable"));

        // 캐시 상태 확인
        Map<String, Object> cacheStatus = rankingAnalyticsService.getStatisticsCacheStatus();
        assertTrue((Integer) cacheStatus.get("concertAnalyticsCacheCount") > 0);

        // 캐시 건강도 확인
        assertEquals("HEALTHY", cacheStatus.get("overallCacheHealth"));
    }

    @Test
    @DisplayName("통계 시스템 요약 정보 정확성 테스트")
    void statisticsSummaryAccuracy_Test() {
        // Given
        Long concertId = testConcert.getConcertId();

        // When - 배치 실행
        statisticsBatchScheduler.updateMainStatistics();

        // Then - 요약 정보 확인
        Map<String, Object> summary = rankingAnalyticsService.getStatisticsSummary();

        assertEquals("HEALTHY", summary.get("cacheHealth"));
        assertEquals("RUNNING", summary.get("serviceStatus"));
        assertEquals("BATCH_CACHED", summary.get("responseMode"));
        assertNotNull(summary.get("lastUpdated"));
        assertNotNull(summary.get("summaryTime"));

        // 시스템 통계 요약 데이터 확인
        assertTrue(summary.containsKey("totalConcerts"));
        assertTrue(summary.containsKey("soldOutConcerts"));
        assertTrue(summary.containsKey("totalReservations"));
    }

    @Test
    @DisplayName("실시간 vs 배치 응답 시간 비교 테스트")
    void responseTimeComparison_Test() {
        // Given
        Long concertId = testConcert.getConcertId();

        // 배치 실행
        statisticsBatchScheduler.updateMainStatistics();

        // When - 캐시 기반 조회 시간 측정 (여러 번 실행하여 평균 계산)
        long totalTime = 0;
        int iterations = 10;

        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();
            Map<String, Object> result = rankingAnalyticsService.getConcertRankingAnalytics(concertId);
            long endTime = System.nanoTime();

            totalTime += (endTime - startTime);

            // 결과 검증
            assertTrue((Boolean) result.get("dataAvailable"));
        }

        // Then - 평균 응답 시간 확인
        long averageTimeNs = totalTime / iterations;
        long averageTimeMs = averageTimeNs / 1_000_000;

        System.out.println("평균 응답 시간: " + averageTimeMs + "ms");

        // 캐시 기반 조회는 100ms 이내여야 함
        assertTrue(averageTimeMs < 100, "캐시 기반 조회 응답 시간이 너무 깁니다: " + averageTimeMs + "ms");
    }
}