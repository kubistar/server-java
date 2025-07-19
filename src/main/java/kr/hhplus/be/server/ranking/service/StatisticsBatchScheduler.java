package kr.hhplus.be.server.ranking.service;

import kr.hhplus.be.server.concert.repository.ConcertRepository;
import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 통계 배치 스케줄러 (Concert 도메인 맞춤)
 *
 * 주기적으로 통계 데이터를 미리 계산하여 Redis에 저장
 * RankingAnalyticsService가 빠르게 조회할 수 있도록 캐시 데이터 제공
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StatisticsBatchScheduler {

    private final ConcertRepository concertRepository;
    private final ReservationRepository reservationRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // Redis 키 상수 (현재 도메인 구조에 맞춤)
    private static final String STATISTICS_PREFIX = "statistics:";
    private static final String CONCERT_ANALYTICS_KEY = STATISTICS_PREFIX + "concert:analytics:";
    private static final String BOOKING_TREND_KEY = STATISTICS_PREFIX + "booking:trend:";
    private static final String SYSTEM_STATS_KEY = STATISTICS_PREFIX + "system:stats";
    private static final String HOURLY_BOOKING_KEY = STATISTICS_PREFIX + "hourly:booking:";
    private static final String DAILY_STATS_KEY = STATISTICS_PREFIX + "daily:";
    private static final String STATS_LAST_UPDATE_KEY = STATISTICS_PREFIX + "last:update";

    /**
     * 주요 통계 배치 - 10분마다 실행
     *
     * 콘서트별 분석 데이터와 예약 트렌드를 업데이트
     * RankingAnalyticsService의 빠른 응답을 위한 핵심 배치
     */
    @Scheduled(fixedRate = 600000) // 10분마다
    @Transactional(readOnly = true)
    public void updateMainStatistics() {
        log.info("주요 통계 배치 시작");

        try {
            // 1. 콘서트별 상세 분석 데이터 업데이트
            updateConcertAnalytics();

            // 2. 시간대별 예약 트렌드 업데이트
            updateBookingTrends();

            // 3. 시스템 전체 통계 업데이트
            updateSystemStatistics();

            // 4. 마지막 업데이트 시간 기록
            redisTemplate.opsForValue().set(STATS_LAST_UPDATE_KEY, LocalDateTime.now().toString());

            log.info("주요 통계 배치 완료");

        } catch (Exception e) {
            log.error("주요 통계 배치 실행 중 오류 발생", e);
        }
    }

    /**
     * 상세 통계 배치 - 1시간마다 실행
     *
     * 더 복잡한 분석과 히스토리 데이터를 생성
     * 시간별/일별 상세 통계 데이터 계산
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다
    @Transactional(readOnly = true)
    public void updateDetailedStatistics() {
        log.info("상세 통계 배치 시작");

        try {
            // 1. 시간별 상세 예약 통계
            calculateHourlyBookingStatistics();

            // 2. 일별 통계 요약
            calculateDailyStatistics();

            // 3. 콘서트 성과 분석
            calculateConcertPerformanceAnalysis();

            log.info("상세 통계 배치 완료");

        } catch (Exception e) {
            log.error("상세 통계 배치 실행 중 오류 발생", e);
        }
    }

    /**
     * 콘서트별 상세 분석 데이터 업데이트
     * RankingAnalyticsService.getConcertRankingAnalytics() 대체용
     */
    private void updateConcertAnalytics() {
        log.info("콘서트 분석 데이터 업데이트 시작");

        List<Concert> allConcerts = concertRepository.findAll();
        int processedCount = 0;

        for (Concert concert : allConcerts) {
            try {
                Map<String, Object> analytics = calculateConcertAnalytics(concert);

                // Redis에 콘서트별 분석 데이터 저장 (1시간 TTL)
                String key = CONCERT_ANALYTICS_KEY + concert.getConcertId();
                redisTemplate.opsForValue().set(key, analytics, 3600, TimeUnit.SECONDS);

                processedCount++;

            } catch (Exception e) {
                log.error("콘서트 분석 데이터 계산 실패: concertId={}", concert.getConcertId(), e);
            }
        }

        log.info("콘서트 분석 데이터 업데이트 완료: {} 콘서트 처리", processedCount);
    }

    /**
     * 개별 콘서트 분석 데이터 계산
     */
    private Map<String, Object> calculateConcertAnalytics(Concert concert) {
        Map<String, Object> analytics = new HashMap<>();

        // 기본 정보
        analytics.put("concertId", concert.getConcertId());
        analytics.put("concertName", concert.getTitle());
        analytics.put("artist", concert.getArtist());
        analytics.put("venue", concert.getVenue());
        analytics.put("concertDate", concert.getConcertDate().toString());
        analytics.put("concertTime", concert.getConcertTime().toString());
        analytics.put("totalSeats", concert.getTotalSeats());
        analytics.put("lastUpdated", LocalDateTime.now().toString());

        // 예약 관련 통계
        Long reservationCount = reservationRepository.countByConcertIdAndStatus(
                concert.getConcertId(), "CONFIRMED");
        analytics.put("totalReservations", reservationCount != null ? reservationCount : 0L);

        // 예약률 계산
        double reservationRate = 0.0;
        if (concert.getTotalSeats() > 0) {
            reservationRate = (double) (reservationCount != null ? reservationCount : 0) / concert.getTotalSeats() * 100;
        }
        analytics.put("reservationRate", Math.round(reservationRate * 100.0) / 100.0); // 소수점 2자리

        // 인기도 랭킹에서의 순위 (Redis 랭킹 데이터 활용)
        Long popularityRank = redisTemplate.opsForZSet()
                .reverseRank("concert:ranking:popularity", concert.getConcertId());
        analytics.put("popularityRank", popularityRank != null ? popularityRank + 1 : null);
        analytics.put("popularityScore", reservationCount != null ? reservationCount.doubleValue() : 0.0);

        // 매진 관련 정보
        if (concert.isSoldOut()) {
            analytics.put("soldOut", true);
            analytics.put("soldOutTime", concert.getSoldOutTime().toString());

            Long durationMinutes = concert.getSoldOutDurationMinutes();
            analytics.put("soldOutDurationMinutes", durationMinutes);

            // 매진 속도 점수 계산
            double soldOutScore = calculateSoldOutScore(concert);
            analytics.put("soldOutScore", soldOutScore);

            // 매진 속도 랭킹에서의 순위
            Long speedRank = redisTemplate.opsForZSet()
                    .reverseRank("concert:ranking:soldout_speed", concert.getConcertId());
            analytics.put("soldOutSpeedRank", speedRank != null ? speedRank + 1 : null);
        } else {
            analytics.put("soldOut", false);
            analytics.put("soldOutScore", 0.0);
            analytics.put("soldOutSpeedRank", null);
        }

        // 예약 가능 상태
        analytics.put("bookingAvailable", concert.isBookingAvailable());
        analytics.put("bookable", concert.isBookable());

        // 현재 예약 속도 계산 (최근 1시간 기준 - 단순화)
        analytics.put("currentBookingSpeedPerHour", calculateRecentBookingSpeed(concert.getConcertId()));

        return analytics;
    }

    /**
     * 시간대별 예약 트렌드 업데이트
     * RankingAnalyticsService.getBookingTrendAnalysis() 대체용
     */
    private void updateBookingTrends() {
        log.info("예약 트렌드 업데이트 시작");

        // 현재 예약 가능한 콘서트들 대상으로만 트렌드 계산
        List<Concert> allConcerts = concertRepository.findAll();
        List<Concert> activeConcerts = allConcerts.stream()
                .filter(Concert::isBookable) // 아직 공연 전인 콘서트들
                .toList();

        int processedCount = 0;

        for (Concert concert : activeConcerts) {
            try {
                // 24시간 트렌드 계산
                Map<String, Object> trend24h = calculateBookingTrend(concert.getConcertId(), 24);
                String key24h = BOOKING_TREND_KEY + concert.getConcertId() + ":24h";
                redisTemplate.opsForValue().set(key24h, trend24h, 1800, TimeUnit.SECONDS); // 30분 TTL

                // 12시간 트렌드 계산
                Map<String, Object> trend12h = calculateBookingTrend(concert.getConcertId(), 12);
                String key12h = BOOKING_TREND_KEY + concert.getConcertId() + ":12h";
                redisTemplate.opsForValue().set(key12h, trend12h, 1800, TimeUnit.SECONDS);

                // 6시간 트렌드 계산
                Map<String, Object> trend6h = calculateBookingTrend(concert.getConcertId(), 6);
                String key6h = BOOKING_TREND_KEY + concert.getConcertId() + ":6h";
                redisTemplate.opsForValue().set(key6h, trend6h, 1800, TimeUnit.SECONDS);

                processedCount++;

            } catch (Exception e) {
                log.error("예약 트렌드 계산 실패: concertId={}", concert.getConcertId(), e);
            }
        }

        log.info("예약 트렌드 업데이트 완료: {} 콘서트 처리", processedCount);
    }

    /**
     * 개별 콘서트의 예약 트렌드 계산
     */
    private Map<String, Object> calculateBookingTrend(Long concertId, int hours) {
        Map<String, Object> trend = new HashMap<>();
        Map<String, Integer> hourlyBookings = new HashMap<>();

        LocalDateTime now = LocalDateTime.now();
        int totalBookings = 0;

        // 지난 N시간 동안의 시간대별 예약 수 조회 (단순화된 버전)
        for (int i = 0; i < hours; i++) {
            LocalDateTime targetTime = now.minusHours(i);
            String hourKey = targetTime.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));

            // 실제로는 시간별 예약 데이터를 별도 테이블이나 집계 시스템에서 조회해야 함
            // 여기서는 단순화된 계산 (실제 구현에서는 최적화 필요)
            int hourlyCount = getHourlyBookingCount(concertId, targetTime);
            hourlyBookings.put(hourKey, hourlyCount);
            totalBookings += hourlyCount;
        }

        trend.put("concertId", concertId);
        trend.put("hourlyBookings", hourlyBookings);
        trend.put("analysisHours", hours);
        trend.put("totalBookings", totalBookings);
        trend.put("averageBookingsPerHour", hours > 0 ? (double) totalBookings / hours : 0.0);
        trend.put("peakHour", findPeakBookingHour(hourlyBookings));
        trend.put("lastUpdated", LocalDateTime.now().toString());

        return trend;
    }

    /**
     * 시스템 전체 통계 업데이트
     * RankingAnalyticsService.getSystemStatistics() 대체용
     */
    private void updateSystemStatistics() {
        log.info("시스템 통계 업데이트 시작");

        try {
            Map<String, Object> stats = new HashMap<>();

            // 전체 콘서트 수
            long totalConcerts = concertRepository.findAll().size();
            stats.put("totalConcerts", totalConcerts);

            // 매진된 콘서트 수
            List<Concert> soldOutConcerts = concertRepository.findBySoldOutTrue();
            stats.put("totalSoldOutConcerts", soldOutConcerts.size());

            // 예약 가능한 콘서트 수
            List<Concert> allConcerts = concertRepository.findAll();
            long availableConcerts = allConcerts.stream()
                    .filter(Concert::isBookable)
                    .count();
            stats.put("totalAvailableConcerts", availableConcerts);

            // 예약 중인 콘서트 수 (예약 기간 내)
            long bookingAvailableConcerts = allConcerts.stream()
                    .filter(Concert::isBookingAvailable)
                    .count();
            stats.put("bookingAvailableConcerts", bookingAvailableConcerts);

            // 전체 예약 수 통계
            List<Object[]> reservationStats = reservationRepository.getReservationCountByConcer();
            long totalReservations = reservationStats.stream()
                    .mapToLong(row -> (Long) row[1])
                    .sum();
            stats.put("totalReservations", totalReservations);

            // 랭킹 데이터 통계
            Long popularityRankingCount = redisTemplate.opsForZSet()
                    .count("concert:ranking:popularity", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            Long soldOutRankingCount = redisTemplate.opsForZSet()
                    .count("concert:ranking:soldout_speed", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

            stats.put("popularityRankingCount", popularityRankingCount);
            stats.put("soldOutSpeedRankingCount", soldOutRankingCount);

            // 평균 통계
            if (totalConcerts > 0) {
                stats.put("averageReservationsPerConcert", (double) totalReservations / totalConcerts);
                stats.put("soldOutRate", (double) soldOutConcerts.size() / totalConcerts * 100);
            } else {
                stats.put("averageReservationsPerConcert", 0.0);
                stats.put("soldOutRate", 0.0);
            }

            // 시스템 상태
            stats.put("systemStatus", "RUNNING");
            stats.put("lastUpdated", LocalDateTime.now().toString());

            // Redis에 저장 (10분 TTL)
            redisTemplate.opsForValue().set(SYSTEM_STATS_KEY, stats, 600, TimeUnit.SECONDS);

            log.info("시스템 통계 업데이트 완료");

        } catch (Exception e) {
            log.error("시스템 통계 업데이트 실패", e);
        }
    }

    /**
     * 시간별 상세 예약 통계 계산
     */
    private void calculateHourlyBookingStatistics() {
        log.info("시간별 예약 통계 계산 시작");

        LocalDateTime now = LocalDateTime.now();
        String dateKey = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 오늘의 시간별 전체 예약 통계 (단순화된 버전)
        Map<String, Object> hourlyStats = new HashMap<>();

        for (int hour = 0; hour < 24; hour++) {
            String hourKey = String.format("%02d", hour);
            // 실제로는 DB에서 해당 시간대의 예약 수를 조회해야 함
            int totalBookingsInHour = getTotalBookingsInHour(now.withHour(hour));
            hourlyStats.put(hourKey, totalBookingsInHour);
        }

        // Redis에 저장
        String key = HOURLY_BOOKING_KEY + dateKey;
        redisTemplate.opsForValue().set(key, hourlyStats, 86400, TimeUnit.SECONDS); // 24시간 TTL

        log.info("시간별 예약 통계 계산 완료");
    }

    /**
     * 일별 통계 요약 계산
     */
    private void calculateDailyStatistics() {
        log.info("일별 통계 계산 시작");

        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        String dateKey = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        Map<String, Object> dailyStats = new HashMap<>();

        // 오늘의 총 예약 수 (단순화된 계산)
        int todayReservations = getTodayReservationsCount();
        dailyStats.put("totalReservations", todayReservations);

        // 오늘 매진된 콘서트 수
        int todaySoldOut = getTodaySoldOutCount();
        dailyStats.put("soldOutConcerts", todaySoldOut);

        // 최고 예약 시간대
        String peakHour = getTodayPeakHour();
        dailyStats.put("peakBookingHour", peakHour);

        dailyStats.put("date", dateKey);
        dailyStats.put("lastUpdated", LocalDateTime.now().toString());

        // Redis에 저장
        String key = DAILY_STATS_KEY + dateKey;
        redisTemplate.opsForValue().set(key, dailyStats, 86400, TimeUnit.SECONDS);

        log.info("일별 통계 계산 완료: date={}", dateKey);
    }

    /**
     * 콘서트 성과 분석
     */
    private void calculateConcertPerformanceAnalysis() {
        log.info("콘서트 성과 분석 시작");

        // 상위 성과 콘서트들 분석
        List<Object[]> topPerformers = reservationRepository.getReservationCountByConcer();

        Map<String, Object> performanceAnalysis = new HashMap<>();
        performanceAnalysis.put("topPerformingConcerts",
                topPerformers.subList(0, Math.min(10, topPerformers.size())));
        performanceAnalysis.put("analysisTime", LocalDateTime.now().toString());

        // Redis에 저장
        redisTemplate.opsForValue().set(STATISTICS_PREFIX + "performance:analysis",
                performanceAnalysis, 3600, TimeUnit.SECONDS);

        log.info("콘서트 성과 분석 완료");
    }

    // ================= 헬퍼 메소드들 =================

    private double calculateSoldOutScore(Concert concert) {
        Long durationMinutes = concert.getSoldOutDurationMinutes();
        if (durationMinutes == null || durationMinutes <= 0) {
            return 0.0;
        }

        // 매진 속도 점수 = 총좌석수 / 매진소요시간(분) * 1000
        return (double) concert.getTotalSeats() / durationMinutes * 1000;
    }

    private int calculateRecentBookingSpeed(Long concertId) {
        // 최근 1시간 예약 수 계산 (실제 구현 시 최적화 필요)
        // 여기서는 단순화된 값 반환
        return 0;
    }

    private int getHourlyBookingCount(Long concertId, LocalDateTime targetTime) {
        // 특정 시간대의 예약 수 조회 (실제로는 시간별 집계 테이블 필요)
        return 0;
    }

    private String findPeakBookingHour(Map<String, Integer> hourlyBookings) {
        return hourlyBookings.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("00");
    }

    private int getTotalBookingsInHour(LocalDateTime targetHour) {
        // 특정 시간의 전체 예약 수 (실제 구현 필요)
        return 0;
    }

    private int getTodayReservationsCount() {
        // 오늘의 예약 수 (실제 구현 필요)
        return 0;
    }

    private int getTodaySoldOutCount() {
        // 오늘 매진된 콘서트 수 (실제 구현 필요)
        return 0;
    }

    private String getTodayPeakHour() {
        // 오늘의 최고 예약 시간대 (실제 구현 필요)
        return "20:00";
    }
}