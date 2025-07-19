package kr.hhplus.be.server.ranking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 랭킹 분석 및 통계 조회 서비스 (배치 기반)
 *
 * StatisticsBatchScheduler에서 미리 계산한 통계 데이터를 빠르게 조회
 * 실시간 계산 없이 캐시된 결과만 반환하여 0.1초 응답 속도 보장
 *
 * 역할:
 * - 배치에서 계산된 콘서트 분석 데이터 조회
 * - 배치에서 계산된 예약 트렌드 데이터 조회
 * - 배치에서 계산된 시스템 통계 데이터 조회
 * - 캐시 상태 확인 및 관리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RankingAnalyticsService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis 키 상수 (StatisticsBatchScheduler와 동일)
    private static final String STATISTICS_PREFIX = "statistics:";
    private static final String CONCERT_ANALYTICS_KEY = STATISTICS_PREFIX + "concert:analytics:";
    private static final String BOOKING_TREND_KEY = STATISTICS_PREFIX + "booking:trend:";
    private static final String SYSTEM_STATS_KEY = STATISTICS_PREFIX + "system:stats";
    private static final String STATS_LAST_UPDATE_KEY = STATISTICS_PREFIX + "last:update";

    /**
     * 콘서트별 상세 랭킹 분석 정보 조회
     *
     * StatisticsBatchScheduler에서 10분마다 계산한 분석 데이터를 조회
     * 실시간 계산 없이 캐시된 결과만 반환하여 빠른 응답 보장
     *
     * @param concertId 조회할 콘서트 ID
     * @return 미리 계산된 콘서트 분석 데이터
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getConcertRankingAnalytics(Long concertId) {
        log.info("콘서트 랭킹 분석 정보 조회 - concertId: {}", concertId);

        try {
            String key = CONCERT_ANALYTICS_KEY + concertId;

            // 배치에서 미리 계산된 데이터 조회 (0.1초 응답)
            Object cachedData = redisTemplate.opsForValue().get(key);

            if (cachedData != null) {
                Map<String, Object> analytics = (Map<String, Object>) cachedData;

                // 데이터 가용성 플래그 추가
                analytics.put("dataAvailable", true);
                analytics.put("responseTime", LocalDateTime.now().toString());

                log.info("콘서트 분석 정보 조회 성공 - concertId: {}", concertId);
                return analytics;
            } else {
                log.warn("콘서트 분석 데이터가 캐시에 없음 - concertId: {}", concertId);
                return createEmptyAnalytics(concertId, "분석 데이터가 아직 계산되지 않았습니다. 잠시 후 다시 시도해주세요.");
            }

        } catch (Exception e) {
            log.error("콘서트 랭킹 분석 정보 조회 실패 - concertId: {}", concertId, e);
            return createEmptyAnalytics(concertId, "분석 정보 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 시간대별 예약 트렌드 분석 조회
     *
     * StatisticsBatchScheduler에서 계산한 시간대별 트렌드 데이터를 조회
     * 지원하는 시간 범위: 6시간, 12시간, 24시간
     *
     * @param concertId 조회할 콘서트 ID
     * @param hours 조회 시간 범위 (6, 12, 24시간 지원)
     * @return 미리 계산된 예약 트렌드 데이터
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getBookingTrendAnalysis(Long concertId, int hours) {
        log.info("예약 트렌드 분석 조회 - concertId: {}, hours: {}", concertId, hours);

        try {
            // 지원하는 시간 범위로 정규화
            String timeRange = normalizeTimeRange(hours);
            String key = BOOKING_TREND_KEY + concertId + ":" + timeRange;

            // 배치에서 미리 계산된 트렌드 데이터 조회
            Object cachedData = redisTemplate.opsForValue().get(key);

            if (cachedData != null) {
                Map<String, Object> trend = (Map<String, Object>) cachedData;

                // 데이터 가용성 플래그 추가
                trend.put("dataAvailable", true);
                trend.put("requestedHours", hours);
                trend.put("actualTimeRange", timeRange);
                trend.put("responseTime", LocalDateTime.now().toString());

                log.info("예약 트렌드 조회 성공 - concertId: {}, timeRange: {}", concertId, timeRange);
                return trend;
            } else {
                log.warn("예약 트렌드 데이터가 캐시에 없음 - concertId: {}, timeRange: {}", concertId, timeRange);
                return createEmptyTrend(concertId, hours, "트렌드 데이터가 아직 계산되지 않았습니다.");
            }

        } catch (Exception e) {
            log.error("예약 트렌드 분석 조회 실패 - concertId: {}, hours: {}", concertId, hours, e);
            return createEmptyTrend(concertId, hours, "트렌드 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 전체 랭킹 시스템 통계 조회
     *
     * StatisticsBatchScheduler에서 계산한 시스템 전체 통계 데이터를 조회
     * 전체 콘서트 수, 매진 콘서트 수, 예약 수 등 종합 통계 제공
     *
     * @return 미리 계산된 시스템 전체 통계 데이터
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSystemStatistics() {
        log.info("시스템 통계 조회");

        try {
            // 배치에서 미리 계산된 시스템 통계 조회
            Object cachedData = redisTemplate.opsForValue().get(SYSTEM_STATS_KEY);

            if (cachedData != null) {
                Map<String, Object> stats = (Map<String, Object>) cachedData;

                // 응답 시간 추가
                stats.put("responseTime", LocalDateTime.now().toString());
                stats.put("dataAvailable", true);

                log.info("시스템 통계 조회 성공");
                return stats;
            } else {
                log.warn("시스템 통계 데이터가 캐시에 없음");
                return createEmptySystemStats("시스템 통계가 아직 계산되지 않았습니다.");
            }

        } catch (Exception e) {
            log.error("시스템 통계 조회 실패", e);
            return createEmptySystemStats("시스템 통계 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 여러 콘서트의 분석 데이터 일괄 조회
     *
     * 여러 콘서트의 분석 정보를 한 번에 조회하여 네트워크 호출 최적화
     * 대시보드나 목록 페이지에서 활용
     *
     * @param concertIds 조회할 콘서트 ID 목록 (최대 50개)
     * @return 콘서트별 분석 데이터 맵
     */
    public Map<Long, Map<String, Object>> getBulkConcertAnalytics(List<Long> concertIds) {
        log.info("다중 콘서트 분석 데이터 조회 - 콘서트 수: {}", concertIds.size());

        if (concertIds.size() > 50) {
            throw new IllegalArgumentException("한 번에 조회할 수 있는 콘서트는 최대 50개입니다.");
        }

        Map<Long, Map<String, Object>> results = new HashMap<>();
        int successCount = 0;

        for (Long concertId : concertIds) {
            try {
                Map<String, Object> analytics = getConcertRankingAnalytics(concertId);
                results.put(concertId, analytics);

                if ((Boolean) analytics.getOrDefault("dataAvailable", false)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("콘서트 분석 데이터 조회 실패 - concertId: {}", concertId, e);
                results.put(concertId, createEmptyAnalytics(concertId, "조회 실패"));
            }
        }

        log.info("다중 콘서트 분석 데이터 조회 완료 - 성공: {}/{}", successCount, concertIds.size());
        return results;
    }

    /**
     * 통계 데이터 캐시 상태 확인
     *
     * 시스템 모니터링 및 장애 진단을 위한 캐시 상태 정보 제공
     * 각 통계 데이터의 존재 여부와 마지막 업데이트 시간 확인
     *
     * @return 캐시 상태 정보
     */
    public Map<String, Object> getStatisticsCacheStatus() {
        log.info("통계 캐시 상태 확인");

        Map<String, Object> status = new HashMap<>();

        try {
            // 시스템 통계 캐시 상태
            boolean systemStatsExists = Boolean.TRUE.equals(redisTemplate.hasKey(SYSTEM_STATS_KEY));
            status.put("systemStatsCached", systemStatsExists);

            // 마지막 업데이트 시간
            String lastUpdate = (String) redisTemplate.opsForValue().get(STATS_LAST_UPDATE_KEY);
            status.put("lastUpdated", lastUpdate);

            // 캐시 키 개수 추정 (샘플링으로 성능 고려)
            java.util.Set<String> analyticsKeys = redisTemplate.keys(CONCERT_ANALYTICS_KEY + "*");
            java.util.Set<String> trendKeys = redisTemplate.keys(BOOKING_TREND_KEY + "*");

            status.put("concertAnalyticsCacheCount", analyticsKeys != null ? analyticsKeys.size() : 0);
            status.put("bookingTrendCacheCount", trendKeys != null ? trendKeys.size() : 0);

            // 전체 캐시 건강도 평가
            boolean allHealthy = systemStatsExists && lastUpdate != null;
            status.put("overallCacheHealth", allHealthy ? "HEALTHY" : "DEGRADED");

            // 캐시 성능 지표
            status.put("checkTime", LocalDateTime.now().toString());

        } catch (Exception e) {
            log.error("통계 캐시 상태 확인 중 오류 발생", e);
            status.put("error", e.getMessage());
            status.put("overallCacheHealth", "ERROR");
        }

        return status;
    }

    /**
     * 특정 콘서트의 통계 캐시 강제 갱신 요청
     *
     * 관리자나 시스템에서 특정 콘서트의 통계를 즉시 갱신하고 싶을 때 사용
     * 기존 캐시를 삭제하여 다음 배치 실행 시 새로 계산되도록 함
     *
     * @param concertId 갱신 요청할 콘서트 ID
     * @return 갱신 요청 결과
     */
    public Map<String, Object> requestStatisticsRefresh(Long concertId) {
        log.info("콘서트 통계 강제 갱신 요청 - concertId: {}", concertId);

        Map<String, Object> result = new HashMap<>();

        try {
            int deletedCount = 0;

            // 콘서트 분석 캐시 삭제
            String analyticsKey = CONCERT_ANALYTICS_KEY + concertId;
            if (Boolean.TRUE.equals(redisTemplate.delete(analyticsKey))) {
                deletedCount++;
            }

            // 트렌드 데이터 캐시 삭제
            String[] trendKeys = {
                    BOOKING_TREND_KEY + concertId + ":6h",
                    BOOKING_TREND_KEY + concertId + ":12h",
                    BOOKING_TREND_KEY + concertId + ":24h"
            };

            for (String key : trendKeys) {
                if (Boolean.TRUE.equals(redisTemplate.delete(key))) {
                    deletedCount++;
                }
            }

            result.put("success", true);
            result.put("deletedCacheCount", deletedCount);
            result.put("message", "콘서트 통계 캐시가 삭제되었습니다. 다음 배치 실행 시 갱신됩니다.");
            result.put("concertId", concertId);
            result.put("requestTime", LocalDateTime.now().toString());
            result.put("nextBatchTime", "최대 10분 후");

            log.info("콘서트 통계 강제 갱신 요청 완료 - concertId: {}, deletedCount: {}", concertId, deletedCount);

        } catch (Exception e) {
            log.error("콘서트 통계 강제 갱신 요청 실패 - concertId: {}", concertId, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 전체 통계 캐시 상태 요약 조회
     *
     * 시스템 대시보드나 모니터링에서 활용할 수 있는 요약 정보
     *
     * @return 통계 시스템 요약 정보
     */
    public Map<String, Object> getStatisticsSummary() {
        log.info("통계 시스템 요약 정보 조회");

        Map<String, Object> summary = new HashMap<>();

        try {
            // 기본 캐시 상태
            Map<String, Object> cacheStatus = getStatisticsCacheStatus();
            summary.put("cacheHealth", cacheStatus.get("overallCacheHealth"));
            summary.put("lastUpdated", cacheStatus.get("lastUpdated"));

            // 시스템 통계 요약
            Map<String, Object> systemStats = getSystemStatistics();
            if ((Boolean) systemStats.getOrDefault("dataAvailable", false)) {
                summary.put("totalConcerts", systemStats.get("totalConcerts"));
                summary.put("soldOutConcerts", systemStats.get("totalSoldOutConcerts"));
                summary.put("totalReservations", systemStats.get("totalReservations"));
            }

            summary.put("serviceStatus", "RUNNING");
            summary.put("responseMode", "BATCH_CACHED");
            summary.put("summaryTime", LocalDateTime.now().toString());

        } catch (Exception e) {
            log.error("통계 시스템 요약 정보 조회 실패", e);
            summary.put("serviceStatus", "ERROR");
            summary.put("error", e.getMessage());
        }

        return summary;
    }


    /**
     * 시간 범위 정규화 (지원하는 범위로 변환)
     */
    private String normalizeTimeRange(int hours) {
        if (hours <= 6) {
            return "6h";
        } else if (hours <= 12) {
            return "12h";
        } else {
            return "24h";
        }
    }

    /**
     * 빈 콘서트 분석 데이터 생성
     */
    private Map<String, Object> createEmptyAnalytics(Long concertId, String message) {
        Map<String, Object> analytics = new HashMap<>();
        analytics.put("concertId", concertId);
        analytics.put("soldOutScore", 0.0);
        analytics.put("popularityScore", 0.0);
        analytics.put("soldOutRank", null);
        analytics.put("popularityRank", null);
        analytics.put("totalReservations", 0L);
        analytics.put("reservationRate", 0.0);
        analytics.put("currentBookingSpeedPerHour", 0);
        analytics.put("soldOut", false);
        analytics.put("bookingAvailable", false);
        analytics.put("lastUpdated", LocalDateTime.now().toString());
        analytics.put("dataAvailable", false);
        analytics.put("message", message);
        return analytics;
    }

    /**
     * 빈 예약 트렌드 데이터 생성
     */
    private Map<String, Object> createEmptyTrend(Long concertId, int hours, String message) {
        Map<String, Object> trend = new HashMap<>();
        trend.put("concertId", concertId);
        trend.put("hourlyBookings", new HashMap<>());
        trend.put("analysisHours", hours);
        trend.put("totalBookings", 0);
        trend.put("averageBookingsPerHour", 0.0);
        trend.put("peakHour", "00");
        trend.put("lastUpdated", LocalDateTime.now().toString());
        trend.put("dataAvailable", false);
        trend.put("message", message);
        return trend;
    }

    /**
     * 빈 시스템 통계 데이터 생성
     */
    private Map<String, Object> createEmptySystemStats(String message) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalConcerts", 0L);
        stats.put("totalSoldOutConcerts", 0L);
        stats.put("totalAvailableConcerts", 0L);
        stats.put("bookingAvailableConcerts", 0L);
        stats.put("totalReservations", 0L);
        stats.put("averageReservationsPerConcert", 0.0);
        stats.put("soldOutRate", 0.0);
        stats.put("popularityRankingCount", 0L);
        stats.put("soldOutSpeedRankingCount", 0L);
        stats.put("systemStatus", "INITIALIZING");
        stats.put("lastUpdated", LocalDateTime.now().toString());
        stats.put("dataAvailable", false);
        stats.put("message", message);
        return stats;
    }
}