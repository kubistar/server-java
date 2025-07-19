package kr.hhplus.be.server.ranking.scheduler;


import kr.hhplus.be.server.ranking.entity.RankingBatchJob;
import kr.hhplus.be.server.ranking.repository.RankingBatchJobRepository;
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
import java.util.List;

/**
 * 랭킹 배치 스케줄러
 *
 * 주기적으로 배치 작업을 실행하여 랭킹 데이터를 업데이트
 * DB 기반의 정확한 데이터로 Redis 랭킹 정보를 갱신
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RankingScheduler {

    private final RankingBatchJobRepository rankingBatchJobRepository;
    private final ConcertRepository concertRepository;
    private final ReservationRepository reservationRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // Redis 키 상수
    private static final String POPULARITY_RANKING_KEY = "concert:ranking:popularity";
    private static final String SOLDOUT_SPEED_RANKING_KEY = "concert:ranking:soldout_speed";
    private static final String RANKING_LAST_UPDATE_KEY = "concert:ranking:last_update";

    /**
     * 메인 배치 스케줄러 - 5분마다 실행
     *
     * 대기 중인 배치 작업들을 처리하여 랭킹 데이터 업데이트
     * 우선순위가 높은 작업부터 순차적으로 처리
     */
    @Scheduled(fixedRate = 300000) // 5분마다 실행
    @Transactional
    public void processRankingBatchJobs() {
        log.info("랭킹 배치 작업 스케줄러 시작");

        try {
            // 대기 중인 작업들을 우선순위 순으로 조회 (최대 50개)
            List<RankingBatchJob> pendingJobs = rankingBatchJobRepository
                    .findByStatusOrderByPriorityDescCreatedAtAsc(RankingBatchJob.JobStatus.PENDING, 50);

            if (pendingJobs.isEmpty()) {
                log.info("처리할 배치 작업이 없습니다.");
                return;
            }

            log.info("처리할 배치 작업 수: {}", pendingJobs.size());

            int successCount = 0;
            int failCount = 0;

            for (RankingBatchJob job : pendingJobs) {
                try {
                    processJob(job);
                    successCount++;
                    log.info("배치 작업 처리 성공: jobId={}, concertId={}, type={}",
                            job.getId(), job.getConcertId(), job.getJobType());

                } catch (Exception e) {
                    failCount++;
                    handleJobFailure(job, e);
                    log.error("배치 작업 처리 실패: jobId={}, concertId={}, type={}",
                            job.getId(), job.getConcertId(), job.getJobType(), e);
                }
            }

            log.info("랭킹 배치 작업 완료 - 성공: {}, 실패: {}", successCount, failCount);

        } catch (Exception e) {
            log.error("랭킹 배치 스케줄러 실행 중 오류 발생", e);
        }
    }

    /**
     * 전체 랭킹 재계산 - 1시간마다 실행
     *
     * 모든 콘서트의 랭킹을 DB 데이터 기반으로 재계산
     * 데이터 정확성을 보장하기 위한 전체 갱신 작업
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다 실행
    @Transactional
    public void recalculateAllRankings() {
        log.info("전체 랭킹 재계산 시작");

        try {
            // 인기도 랭킹 재계산
            recalculatePopularityRanking();

            // 매진 속도 랭킹 재계산
            recalculateSoldOutSpeedRanking();

            // 마지막 업데이트 시간 기록
            redisTemplate.opsForValue().set(RANKING_LAST_UPDATE_KEY, LocalDateTime.now().toString());

            log.info("전체 랭킹 재계산 완료");

        } catch (Exception e) {
            log.error("전체 랭킹 재계산 중 오류 발생", e);
        }
    }

    /**
     * 개별 배치 작업 처리
     *
     * @param job 처리할 배치 작업
     */
    private void processJob(RankingBatchJob job) {
        // 작업 시작 상태로 변경
        job.startProcessing();
        rankingBatchJobRepository.save(job);

        try {
            switch (job.getJobType()) {
                case POPULARITY_UPDATE:
                    updatePopularityRanking(job.getConcertId());
                    break;
                case SOLDOUT_SPEED_UPDATE:
                    updateSoldOutSpeedRanking(job.getConcertId());
                    break;
                default:
                    throw new IllegalArgumentException("지원하지 않는 작업 타입: " + job.getJobType());
            }

            // 작업 완료 처리
            job.completeProcessing();
            rankingBatchJobRepository.save(job);

        } catch (Exception e) {
            // 작업 실패 처리는 상위에서 처리
            throw e;
        }
    }

    /**
     * 인기도 랭킹 업데이트 (개별 콘서트)
     *
     * @param concertId 업데이트할 콘서트 ID
     */
    private void updatePopularityRanking(Long concertId) {
        log.info("인기도 랭킹 업데이트 시작: concertId={}", concertId);

        // DB에서 정확한 예약 수 조회
        Long reservationCount = reservationRepository.countByConcertIdAndStatus(
                concertId, "CONFIRMED");

        if (reservationCount == null) {
            reservationCount = 0L;
        }

        // Redis 인기도 랭킹에 업데이트
        redisTemplate.opsForZSet().add(POPULARITY_RANKING_KEY, concertId, reservationCount.doubleValue());

        log.info("인기도 랭킹 업데이트 완료: concertId={}, reservationCount={}", concertId, reservationCount);
    }

    /**
     * 매진 속도 랭킹 업데이트 (개별 콘서트)
     *
     * @param concertId 업데이트할 콘서트 ID
     */
    private void updateSoldOutSpeedRanking(Long concertId) {
        log.info("매진 속도 랭킹 업데이트 시작: concertId={}", concertId);

        Concert concert = concertRepository.findById(concertId).orElse(null);
        if (concert == null) {
            log.warn("콘서트를 찾을 수 없음: concertId={}", concertId);
            return;
        }

        // 매진 여부 확인
        if (concert.isSoldOut()) {
            // 매진 속도 점수 계산 (예: 총좌석수 / 매진소요시간(분))
            double speedScore = calculateSoldOutSpeedScore(concert);

            // Redis 매진 속도 랭킹에 업데이트
            redisTemplate.opsForZSet().add(SOLDOUT_SPEED_RANKING_KEY, concertId, speedScore);

            log.info("매진 속도 랭킹 업데이트 완료: concertId={}, speedScore={}", concertId, speedScore);
        } else {
            log.info("콘서트가 아직 매진되지 않음: concertId={}", concertId);
        }
    }

    /**
     * 전체 인기도 랭킹 재계산
     */
    private void recalculatePopularityRanking() {
        log.info("전체 인기도 랭킹 재계산 시작");

        // 기존 랭킹 초기화
        redisTemplate.delete(POPULARITY_RANKING_KEY);

        // 모든 콘서트의 예약 수 조회 및 랭킹 업데이트
        List<Object[]> concertReservations = reservationRepository.getReservationCountByConcer();

        for (Object[] row : concertReservations) {
            Long concertId = (Long) row[0];
            Long reservationCount = (Long) row[1];

            redisTemplate.opsForZSet().add(POPULARITY_RANKING_KEY, concertId, reservationCount.doubleValue());
        }

        log.info("전체 인기도 랭킹 재계산 완료: {} 콘서트 처리", concertReservations.size());
    }

    /**
     * 전체 매진 속도 랭킹 재계산
     */
    private void recalculateSoldOutSpeedRanking() {
        log.info("전체 매진 속도 랭킹 재계산 시작");

        // 기존 랭킹 초기화
        redisTemplate.delete(SOLDOUT_SPEED_RANKING_KEY);

        // 매진된 콘서트들 조회
        List<Concert> soldOutConcerts = concertRepository.findBySoldOutTrue();

        for (Concert concert : soldOutConcerts) {
            double speedScore = calculateSoldOutSpeedScore(concert);
            redisTemplate.opsForZSet().add(SOLDOUT_SPEED_RANKING_KEY, concert.getId(), speedScore);
        }

        log.info("전체 매진 속도 랭킹 재계산 완료: {} 콘서트 처리", soldOutConcerts.size());
    }

    /**
     * 매진 속도 점수 계산
     *
     * @param concert 콘서트 정보
     * @return 매진 속도 점수 (높을수록 빠름)
     */
    private double calculateSoldOutSpeedScore(Concert concert) {
        if (concert.getSoldOutTime() == null || concert.getBookingStartTime() == null) {
            return 0.0;
        }

        // 매진 소요 시간 (분)
        long soldOutMinutes = java.time.Duration.between(
                concert.getBookingStartTime(),
                concert.getSoldOutTime()
        ).toMinutes();

        // 매진 속도 점수 = 총좌석수 / 매진소요시간 * 1000 (소수점 처리를 위한 배수)
        if (soldOutMinutes <= 0) {
            soldOutMinutes = 1; // 0으로 나누기 방지
        }

        return (double) concert.getTotalSeats() / soldOutMinutes * 1000;
    }

    /**
     * 배치 작업 실패 처리
     *
     * @param job 실패한 작업
     * @param exception 발생한 예외
     */
    private void handleJobFailure(RankingBatchJob job, Exception exception) {
        try {
            job.failProcessing(exception.getMessage());
            rankingBatchJobRepository.save(job);

            // 3회 이상 실패한 작업은 중단 처리
            if (job.getRetryCount() >= 3) {
                job.abandon();
                rankingBatchJobRepository.save(job);
                log.warn("배치 작업 최대 재시도 횟수 초과로 중단: jobId={}", job.getId());
            }
        } catch (Exception e) {
            log.error("배치 작업 실패 처리 중 오류 발생: jobId={}", job.getId(), e);
        }
    }
}