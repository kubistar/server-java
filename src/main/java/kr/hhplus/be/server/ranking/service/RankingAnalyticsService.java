package kr.hhplus.be.server.ranking.service;

import kr.hhplus.be.server.ranking.script.RankingLuaScripts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 랭킹 분석 및 통계 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RankingAnalyticsService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 콘서트별 상세 랭킹 분석 정보 조회
     */
    public Map<String, Object> getConcertRankingAnalytics(Long concertId) {
        try {
            DefaultRedisScript<List> script = new DefaultRedisScript<>();
            script.setScriptText(RankingLuaScripts.GET_RANKING_DETAILS_SCRIPT);
            script.setResultType(List.class);

            List<Object> result = redisTemplate.execute(script,
                    Collections.emptyList(),
                    String.valueOf(concertId));

            if (result == null || result.isEmpty()) {
                return new HashMap<>();
            }

            Map<String, Object> analytics = new HashMap<>();
            analytics.put("concertId", concertId);
            analytics.put("soldOutScore", result.get(0));
            analytics.put("popularityScore", result.get(1));
            analytics.put("soldOutRank", result.get(2) != null ? (Long) result.get(2) + 1 : null); // 0-based to 1-based
            analytics.put("popularityRank", result.get(3) != null ? (Long) result.get(3) + 1 : null);
            analytics.put("currentBookingSpeedPerMinute", result.get(4));
            analytics.put("lastUpdated", LocalDateTime.now());

            return analytics;

        } catch (Exception e) {
            log.error("콘서트 랭킹 분석 정보 조회 실패 - concertId: {}", concertId, e);
            return new HashMap<>();
        }
    }

    /**
     * 시간대별 예약 트렌드 분석
     */
    public Map<String, Object> getBookingTrendAnalysis(Long concertId, int hours) {
        try {
            Map<String, Object> trend = new HashMap<>();

            // 지난 N시간 동안의 시간대별 예약 수 조회
            LocalDateTime now = LocalDateTime.now();
            Map<String, Integer> hourlyBookings = new HashMap<>();

            for (int i = 0; i < hours; i++) {
                LocalDateTime targetTime = now.minusHours(i);
                String hourKey = targetTime.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));

                // 해당 시간대의 모든 분단위 데이터 합계
                int totalBookings = 0;
                for (int minute = 0; minute < 60; minute++) {
                    String minuteKey = hourKey + String.format("%02d", minute);
                    String countKey = String.format("concert:booking:count:%d:%s", concertId, minuteKey);

                    String count = (String) redisTemplate.opsForValue().get(countKey);
                    if (count != null) {
                        totalBookings += Integer.parseInt(count);
                    }
                }

                hourlyBookings.put(hourKey, totalBookings);
            }

            trend.put("concertId", concertId);
            trend.put("hourlyBookings", hourlyBookings);
            trend.put("analysisHours", hours);
            trend.put("totalBookings", hourlyBookings.values().stream().mapToInt(Integer::intValue).sum());
            trend.put("averageBookingsPerHour",
                    hourlyBookings.values().stream().mapToInt(Integer::intValue).average().orElse(0.0));

            return trend;

        } catch (Exception e) {
            log.error("예약 트렌드 분석 실패 - concertId: {}, hours: {}", concertId, hours, e);
            return new HashMap<>();
        }
    }

    /**
     * 전체 랭킹 시스템 통계
     */
    public Map<String, Object> getSystemStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // 각 랭킹별 콘서트 수
            Long soldOutCount = redisTemplate.opsForZSet().count("concert:ranking:soldout_speed", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            Long popularityCount = redisTemplate.opsForZSet().count("concert:ranking:popularity", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

            stats.put("totalSoldOutConcerts", soldOutCount);
            stats.put("totalPopularityConcerts", popularityCount);
            stats.put("systemStatus", "RUNNING");
            stats.put("lastUpdated", LocalDateTime.now());

            return stats;

        } catch (Exception e) {
            log.error("시스템 통계 조회 실패", e);
            return new HashMap<>();
        }
    }
}