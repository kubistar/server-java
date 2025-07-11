package kr.hhplus.be.server.ranking.service;

import kr.hhplus.be.server.ranking.domain.ConcertRanking;
import kr.hhplus.be.server.ranking.domain.RankingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConcertRankingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ZSetOperations<String, Object> zSetOperations;

    // Redis Key 상수
    private static final String RANKING_SOLDOUT_SPEED = "concert:ranking:soldout_speed";
    private static final String RANKING_BOOKING_SPEED = "concert:ranking:booking_speed";
    private static final String RANKING_POPULARITY = "concert:ranking:popularity";
    private static final String CONCERT_INFO = "concert:info:%d";
    private static final String CONCERT_STATS = "concert:stats:%d";
    private static final String BOOKING_COUNT = "concert:booking:count:%d:%s";
    private static final String BOOKING_SPEED = "concert:booking:speed:%d";

    /**
     * 콘서트 예약 시 실시간 랭킹 업데이트
     */
    public void updateBookingRanking(Long concertId) {
        try {
            // 1. 실시간 예약 카운터 증가
            incrementBookingCount(concertId);

            // 2. 실시간 예약 속도 계산 및 업데이트
            updateRealTimeBookingSpeed(concertId);

            // 3. 종합 인기도 랭킹 업데이트
            updatePopularityRanking(concertId);

        } catch (Exception e) {
            log.error("예약 랭킹 업데이트 실패 - concertId: {}", concertId, e);
        }
    }

    /**
     * 콘서트 매진 시 매진 속도 랭킹 업데이트
     */
    public void updateSoldOutRanking(Long concertId, LocalDateTime bookingStartTime, LocalDateTime soldOutTime) {
        try {
            // 매진 소요 시간 계산 (분 단위)
            long soldOutDurationMinutes = Duration.between(bookingStartTime, soldOutTime).toMinutes();

            // 매진 속도 점수 계산 (매진이 빠를수록 높은 점수)
            double soldOutScore = calculateSoldOutScore(soldOutDurationMinutes);

            // Redis에 매진 속도 랭킹 업데이트
            zSetOperations.add(RANKING_SOLDOUT_SPEED, concertId.toString(), soldOutScore);

            // 콘서트 통계 정보 저장
            saveConcertStats(concertId, bookingStartTime, soldOutTime, soldOutDurationMinutes);

            log.info("매진 랭킹 업데이트 완료 - concertId: {}, 매진시간: {}분, 점수: {}",
                    concertId, soldOutDurationMinutes, soldOutScore);

        } catch (Exception e) {
            log.error("매진 랭킹 업데이트 실패 - concertId: {}", concertId, e);
        }
    }

    /**
     * 랭킹 조회 (상위 N개)
     */
    public List<ConcertRanking> getTopRanking(RankingType type, int limit) {
        try {
            String rankingKey = getRankingKey(type);

            // Redis에서 상위 랭킹 조회 (점수 높은 순)
            Set<ZSetOperations.TypedTuple<Object>> rankings =
                    zSetOperations.reverseRangeWithScores(rankingKey, 0, limit - 1);

            List<ConcertRanking> result = new ArrayList<>();
            int rank = 1;

            for (ZSetOperations.TypedTuple<Object> tuple : rankings) {
                // Redis에서 반환되는 값을 Long으로 안전하게 변환
                Object value = tuple.getValue();
                Long concertId = Long.valueOf(value.toString());
                Double score = tuple.getScore();

                ConcertRanking ranking = ConcertRanking.builder()
                        .rank(rank++)
                        .concertId(concertId)
                        .score(score)
                        .type(type)
                        .build();

                result.add(ranking);
            }

            return result;

        } catch (Exception e) {
            log.error("랭킹 조회 실패 - type: {}, limit: {}", type, limit, e);
            return new ArrayList<>();
        }
    }

    /**
     * 특정 콘서트의 실시간 예약 속도 조회
     */
    public double getRealTimeBookingSpeed(Long concertId) {
        try {
            String speedKey = String.format(BOOKING_SPEED, concertId);

            // 현재 시간 기준 최근 5분간 데이터 조회
            long currentTime = System.currentTimeMillis() / 1000;
            long fiveMinutesAgo = currentTime - 300; // 5분 = 300초

            Set<ZSetOperations.TypedTuple<Object>> speedData =
                    zSetOperations.rangeByScoreWithScores(speedKey, fiveMinutesAgo, currentTime);

            if (speedData.isEmpty()) {
                return 0.0;
            }

            // 최근 5분간 총 예약 수 계산 - 안전한 타입 변환
            double totalBookings = speedData.stream()
                    .mapToDouble(tuple -> {
                        Object value = tuple.getValue();
                        if (value instanceof Number) {
                            return ((Number) value).doubleValue();
                        } else {
                            try {
                                return Double.parseDouble(value.toString());
                            } catch (NumberFormatException e) {
                                log.warn("예약 속도 데이터 파싱 실패 - concertId: {}, value: {}", concertId, value);
                                return 0.0;
                            }
                        }
                    })
                    .sum();

            // 분당 예약 속도 계산
            return totalBookings / 5.0;

        } catch (Exception e) {
            log.error("실시간 예약 속도 조회 실패 - concertId: {}", concertId, e);
            return 0.0;
        }
    }

    /**
     * 콘서트 상태 업데이트 (예약 시작, 매진 등)
     */
    public void updateConcertStatus(Long concertId, String status) {
        try {
            String infoKey = String.format(CONCERT_INFO, concertId);
            redisTemplate.opsForHash().put(infoKey, "status", status);

            if ("BOOKING".equals(status)) {
                redisTemplate.opsForHash().put(infoKey, "booking_start_time",
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }

        } catch (Exception e) {
            log.error("콘서트 상태 업데이트 실패 - concertId: {}, status: {}", concertId, status, e);
        }
    }

    // Private 메서드들

    private void incrementBookingCount(Long concertId) {
        String currentMinute = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String countKey = String.format(BOOKING_COUNT, concertId, currentMinute);

        redisTemplate.opsForValue().increment(countKey);
        redisTemplate.expire(countKey, Duration.ofHours(1)); // 1시간 후 자동 삭제
    }

    private void updateRealTimeBookingSpeed(Long concertId) {
        String speedKey = String.format(BOOKING_SPEED, concertId);
        String currentMinute = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String countKey = String.format(BOOKING_COUNT, concertId, currentMinute);

        // 현재 분의 예약 수 조회 - 안전한 타입 변환
        Object bookingCountObj = redisTemplate.opsForValue().get(countKey);
        double bookingCount = 0.0;

        if (bookingCountObj != null) {
            if (bookingCountObj instanceof Number) {
                bookingCount = ((Number) bookingCountObj).doubleValue();
            } else {
                try {
                    bookingCount = Double.parseDouble(bookingCountObj.toString());
                } catch (NumberFormatException e) {
                    log.warn("예약 카운트 파싱 실패 - concertId: {}, value: {}", concertId, bookingCountObj);
                    bookingCount = 0.0;
                }
            }
        }

        // 현재 타임스탬프로 예약 수 저장
        long currentTimestamp = System.currentTimeMillis() / 1000;
        zSetOperations.add(speedKey, String.valueOf(bookingCount), currentTimestamp);

        // 5분 이전 데이터 제거
        long fiveMinutesAgo = currentTimestamp - 300;
        zSetOperations.removeRangeByScore(speedKey, 0, fiveMinutesAgo);
    }

    private void updatePopularityRanking(Long concertId) {
        // 실시간 예약 속도 조회
        double bookingSpeed = getRealTimeBookingSpeed(concertId);

        // 매진 속도 점수 조회 (없으면 0)
        Double soldOutScore = zSetOperations.score(RANKING_SOLDOUT_SPEED, concertId.toString());
        if (soldOutScore == null) soldOutScore = 0.0;

        // 종합 인기도 점수 계산 (매진 속도에 더 높은 가중치)
        double popularityScore = (soldOutScore * 0.7) + (bookingSpeed * 10 * 0.3); // 예약속도 가중치 낮춤

        // 인기도 랭킹 업데이트
        zSetOperations.add(RANKING_POPULARITY, concertId.toString(), popularityScore);

        // 디버깅 로그
        log.debug("인기도 업데이트 - 콘서트: {}, 매진점수: {}, 예약속도: {}, 최종점수: {}",
                concertId, soldOutScore, bookingSpeed, popularityScore);
    }

    private double calculateSoldOutScore(long soldOutDurationMinutes) {
        // 매진 소요 시간이 짧을수록 높은 점수
        // 최소 1분으로 제한하여 0으로 나누기 방지
        return 10000.0 / Math.max(soldOutDurationMinutes, 1);
    }

    private void saveConcertStats(Long concertId, LocalDateTime bookingStartTime,
                                  LocalDateTime soldOutTime, long durationMinutes) {
        String statsKey = String.format(CONCERT_STATS, concertId);

        Map<String, String> statsData = new HashMap<>();
        statsData.put("soldout_time", soldOutTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        statsData.put("booking_duration_minutes", String.valueOf(durationMinutes));
        statsData.put("booking_start_time", bookingStartTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        redisTemplate.opsForHash().putAll(statsKey, statsData);

        // 통계 데이터는 7일 후 자동 삭제
        redisTemplate.expire(statsKey, Duration.ofDays(7));
    }

    private String getRankingKey(RankingType type) {
        return switch (type) {
            case SOLDOUT_SPEED -> RANKING_SOLDOUT_SPEED;
            case BOOKING_SPEED -> RANKING_BOOKING_SPEED;
            case POPULARITY -> RANKING_POPULARITY;
        };
    }
}