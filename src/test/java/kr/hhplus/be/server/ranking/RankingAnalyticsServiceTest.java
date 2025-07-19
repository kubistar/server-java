package kr.hhplus.be.server.ranking;

import kr.hhplus.be.server.ranking.service.RankingAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RankingAnalyticsService 테스트")
class RankingAnalyticsServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RankingAnalyticsService rankingAnalyticsService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("콘서트 분석 정보 조회 - 캐시 데이터 존재")
    void getConcertRankingAnalytics_CacheExists() {
        // Given
        Long concertId = 1L;
        Map<String, Object> cachedAnalytics = createMockAnalyticsData(concertId);

        when(valueOperations.get("statistics:concert:analytics:" + concertId))
                .thenReturn(cachedAnalytics);

        // When
        Map<String, Object> result = rankingAnalyticsService.getConcertRankingAnalytics(concertId);

        // Then
        assertNotNull(result);
        assertEquals(concertId, result.get("concertId"));
        assertEquals("블랙핑크 월드투어", result.get("concertName")); // 수정: 블랙핑크로 변경! 💕
        assertEquals(50L, result.get("totalReservations"));
        assertEquals(50.0, result.get("reservationRate"));
        assertEquals(true, result.get("dataAvailable"));
        assertNotNull(result.get("responseTime"));

        verify(valueOperations).get("statistics:concert:analytics:" + concertId);
    }

    @Test
    @DisplayName("콘서트 분석 정보 조회 - 캐시 데이터 없음")
    void getConcertRankingAnalytics_CacheNotExists() {
        // Given
        Long concertId = 1L;
        when(valueOperations.get("statistics:concert:analytics:" + concertId))
                .thenReturn(null);

        // When
        Map<String, Object> result = rankingAnalyticsService.getConcertRankingAnalytics(concertId);

        // Then
        assertNotNull(result);
        assertEquals(concertId, result.get("concertId"));
        assertEquals(0.0, result.get("soldOutScore"));
        assertEquals(0.0, result.get("popularityScore"));
        assertEquals(false, result.get("dataAvailable"));
        assertEquals("분석 데이터가 아직 계산되지 않았습니다. 잠시 후 다시 시도해주세요.",
                result.get("message"));

        verify(valueOperations).get("statistics:concert:analytics:" + concertId);
    }

    @Test
    @DisplayName("예약 트렌드 분석 조회 - 24시간")
    void getBookingTrendAnalysis_24Hours() {
        // Given
        Long concertId = 1L;
        int hours = 24;
        Map<String, Object> cachedTrend = createMockTrendData(concertId, "24h");

        when(valueOperations.get("statistics:booking:trend:" + concertId + ":24h"))
                .thenReturn(cachedTrend);

        // When
        Map<String, Object> result = rankingAnalyticsService.getBookingTrendAnalysis(concertId, hours);

        // Then
        assertNotNull(result);
        assertEquals(concertId, result.get("concertId"));
        assertEquals(24, result.get("analysisHours"));
        assertEquals("24h", result.get("actualTimeRange"));
        assertEquals(hours, result.get("requestedHours"));
        assertEquals(true, result.get("dataAvailable"));
        assertNotNull(result.get("hourlyBookings"));

        verify(valueOperations).get("statistics:booking:trend:" + concertId + ":24h");
    }

    @Test
    @DisplayName("예약 트렌드 분석 조회 - 시간 범위 정규화 (6시간 -> 6h)")
    void getBookingTrendAnalysis_TimeRangeNormalization() {
        // Given
        Long concertId = 1L;
        int hours = 5; // 5시간 요청하면 6h로 정규화
        Map<String, Object> cachedTrend = createMockTrendData(concertId, "6h");

        when(valueOperations.get("statistics:booking:trend:" + concertId + ":6h"))
                .thenReturn(cachedTrend);

        // When
        Map<String, Object> result = rankingAnalyticsService.getBookingTrendAnalysis(concertId, hours);

        // Then
        assertNotNull(result);
        assertEquals("6h", result.get("actualTimeRange"));
        assertEquals(5, result.get("requestedHours")); // 원래 요청값

        verify(valueOperations).get("statistics:booking:trend:" + concertId + ":6h");
    }

    @Test
    @DisplayName("시스템 통계 조회 - 정상 동작")
    void getSystemStatistics_Success() {
        // Given
        Map<String, Object> cachedStats = createMockSystemStats();
        when(valueOperations.get("statistics:system:stats")).thenReturn(cachedStats);

        // When
        Map<String, Object> result = rankingAnalyticsService.getSystemStatistics();

        // Then
        assertNotNull(result);
        assertEquals(100L, result.get("totalConcerts"));
        assertEquals(25L, result.get("totalSoldOutConcerts"));
        assertEquals(5000L, result.get("totalReservations"));
        assertEquals("RUNNING", result.get("systemStatus"));
        assertEquals(true, result.get("dataAvailable"));
        assertNotNull(result.get("responseTime"));

        verify(valueOperations).get("statistics:system:stats");
    }

    @Test
    @DisplayName("다중 콘서트 분석 데이터 일괄 조회")
    void getBulkConcertAnalytics_Success() {
        // Given
        List<Long> concertIds = Arrays.asList(1L, 2L, 3L);

        // 각 콘서트별 캐시 데이터 설정
        when(valueOperations.get("statistics:concert:analytics:1"))
                .thenReturn(createMockAnalyticsData(1L));
        when(valueOperations.get("statistics:concert:analytics:2"))
                .thenReturn(createMockAnalyticsData(2L));
        when(valueOperations.get("statistics:concert:analytics:3"))
                .thenReturn(null); // 3번 콘서트는 캐시 없음

        // When
        Map<Long, Map<String, Object>> result = rankingAnalyticsService.getBulkConcertAnalytics(concertIds);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());

        // 1번 콘서트 - 캐시 존재
        assertTrue((Boolean) result.get(1L).get("dataAvailable"));
        assertEquals("블랙핑크 월드투어", result.get(1L).get("concertName")); // 수정: 블랙핑크로 변경! 🖤💖

        // 2번 콘서트 - 캐시 존재
        assertTrue((Boolean) result.get(2L).get("dataAvailable"));

        // 3번 콘서트 - 캐시 없음
        assertFalse((Boolean) result.get(3L).get("dataAvailable"));

        verify(valueOperations, times(3)).get(startsWith("statistics:concert:analytics:"));
    }

    @Test
    @DisplayName("다중 콘서트 분석 - 최대 개수 초과")
    void getBulkConcertAnalytics_TooMany() {
        // Given
        List<Long> concertIds = new ArrayList<>();
        for (long i = 1; i <= 51; i++) { // 51개 (최대 50개 초과)
            concertIds.add(i);
        }

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> rankingAnalyticsService.getBulkConcertAnalytics(concertIds)
        );

        assertEquals("한 번에 조회할 수 있는 콘서트는 최대 50개입니다.", exception.getMessage());
    }

    @Test
    @DisplayName("캐시 상태 확인")
    void getStatisticsCacheStatus_Success() {
        // Given
        when(redisTemplate.hasKey("statistics:system:stats")).thenReturn(true);
        when(valueOperations.get("statistics:last:update"))
                .thenReturn(LocalDateTime.now().toString());

        // keys() 메서드 Mock
        Set<String> analyticsKeys = Set.of(
                "statistics:concert:analytics:1",
                "statistics:concert:analytics:2"
        );
        Set<String> trendKeys = Set.of(
                "statistics:booking:trend:1:24h",
                "statistics:booking:trend:2:12h"
        );

        when(redisTemplate.keys("statistics:concert:analytics:*")).thenReturn(analyticsKeys);
        when(redisTemplate.keys("statistics:booking:trend:*")).thenReturn(trendKeys);

        // When
        Map<String, Object> result = rankingAnalyticsService.getStatisticsCacheStatus();

        // Then
        assertNotNull(result);
        assertEquals(true, result.get("systemStatsCached"));
        assertEquals(2, result.get("concertAnalyticsCacheCount"));
        assertEquals(2, result.get("bookingTrendCacheCount"));
        assertEquals("HEALTHY", result.get("overallCacheHealth"));
        assertNotNull(result.get("lastUpdated"));
    }

    @Test
    @DisplayName("통계 캐시 강제 갱신 요청")
    void requestStatisticsRefresh_Success() {
        // Given
        Long concertId = 1L;

        // 개별 키 삭제 결과 설정 (더 구체적으로)
        when(redisTemplate.delete("statistics:concert:analytics:" + concertId)).thenReturn(true);
        when(redisTemplate.delete("statistics:booking:trend:" + concertId + ":6h")).thenReturn(true);
        when(redisTemplate.delete("statistics:booking:trend:" + concertId + ":12h")).thenReturn(true);
        when(redisTemplate.delete("statistics:booking:trend:" + concertId + ":24h")).thenReturn(true);

        // When
        Map<String, Object> result = rankingAnalyticsService.requestStatisticsRefresh(concertId);

        // Then
        assertNotNull(result);
        assertEquals(true, result.get("success"));
        assertEquals(concertId, result.get("concertId"));
        assertEquals(4, result.get("deletedCacheCount")); // analytics + 3개 trend 키
        assertNotNull(result.get("requestTime"));

        // 4개 키 삭제 확인 (analytics + 6h/12h/24h trend)
        verify(redisTemplate).delete("statistics:concert:analytics:" + concertId);
        verify(redisTemplate).delete("statistics:booking:trend:" + concertId + ":6h");
        verify(redisTemplate).delete("statistics:booking:trend:" + concertId + ":12h");
        verify(redisTemplate).delete("statistics:booking:trend:" + concertId + ":24h");
    }

    @Test
    @DisplayName("통계 시스템 요약 정보 조회")
    void getStatisticsSummary_Success() {
        // Given
        // 캐시 상태 Mock
        when(redisTemplate.hasKey("statistics:system:stats")).thenReturn(true);
        when(valueOperations.get("statistics:last:update"))
                .thenReturn(LocalDateTime.now().toString());
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("key1", "key2"));

        // 시스템 통계 Mock
        Map<String, Object> systemStats = createMockSystemStats();
        when(valueOperations.get("statistics:system:stats")).thenReturn(systemStats);

        // When
        Map<String, Object> result = rankingAnalyticsService.getStatisticsSummary();

        // Then
        assertNotNull(result);
        assertEquals("HEALTHY", result.get("cacheHealth"));
        assertEquals(100L, result.get("totalConcerts"));
        assertEquals(25L, result.get("soldOutConcerts"));
        assertEquals(5000L, result.get("totalReservations"));
        assertEquals("RUNNING", result.get("serviceStatus"));
        assertEquals("BATCH_CACHED", result.get("responseMode"));
        assertNotNull(result.get("summaryTime"));
    }

    private Map<String, Object> createMockAnalyticsData(Long concertId) {
        Map<String, Object> analytics = new HashMap<>();
        analytics.put("concertId", concertId);
        analytics.put("concertName", "블랙핑크 월드투어");
        analytics.put("artist", "blackpink");
        analytics.put("venue", "고양");
        analytics.put("totalSeats", 100);
        analytics.put("totalReservations", 50L);
        analytics.put("reservationRate", 50.0);
        analytics.put("popularityRank", 1L);
        analytics.put("popularityScore", 50.0);
        analytics.put("soldOut", false);
        analytics.put("soldOutScore", 0.0);
        analytics.put("soldOutSpeedRank", null);
        analytics.put("bookingAvailable", true);
        analytics.put("bookable", true);
        analytics.put("currentBookingSpeedPerHour", 5);
        analytics.put("lastUpdated", LocalDateTime.now().toString());
        return analytics;
    }

    private Map<String, Object> createMockTrendData(Long concertId, String timeRange) {
        Map<String, Object> trend = new HashMap<>();
        trend.put("concertId", concertId);
        trend.put("hourlyBookings", Map.of("2025071910", 10, "2025071911", 15, "2025071912", 8));
        trend.put("analysisHours", timeRange.equals("24h") ? 24 : timeRange.equals("12h") ? 12 : 6);
        trend.put("totalBookings", 33);
        trend.put("averageBookingsPerHour", 11.0);
        trend.put("peakHour", "11");
        trend.put("lastUpdated", LocalDateTime.now().toString());
        return trend;
    }

    private Map<String, Object> createMockSystemStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalConcerts", 100L);
        stats.put("totalSoldOutConcerts", 25L);
        stats.put("totalAvailableConcerts", 75L);
        stats.put("bookingAvailableConcerts", 50L);
        stats.put("totalReservations", 5000L);
        stats.put("averageReservationsPerConcert", 50.0);
        stats.put("soldOutRate", 25.0);
        stats.put("popularityRankingCount", 100L);
        stats.put("soldOutSpeedRankingCount", 25L);
        stats.put("systemStatus", "RUNNING");
        stats.put("lastUpdated", LocalDateTime.now().toString());
        return stats;
    }
}