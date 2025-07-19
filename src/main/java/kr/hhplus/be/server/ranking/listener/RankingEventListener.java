package kr.hhplus.be.server.ranking.listener;

import kr.hhplus.be.server.concert.event.ConcertSoldOutEvent;
import kr.hhplus.be.server.reservation.event.ReservationCompletedEvent;
import kr.hhplus.be.server.ranking.entity.RankingBatchJob;
import kr.hhplus.be.server.ranking.repository.RankingBatchJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * 랭킹 관련 이벤트 리스너
 *
 * 실시간 랭킹 업데이트 대신 배치 작업을 위한 작업 큐에 등록하는 방식
 * 트랜잭션 완료 후 배치 작업 대상으로 콘서트를 마킹
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RankingEventListener {

    private final RankingBatchJobRepository rankingBatchJobRepository;

    /**
     * 예약 완료 이벤트 처리
     *
     * 실시간 랭킹 업데이트 대신 해당 콘서트를 배치 작업 대상으로 등록
     * 인기도 랭킹과 예약 속도 관련 데이터 업데이트 필요
     *
     * @param event 예약 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReservationCompleted(ReservationCompletedEvent event) {
        log.info("예약 완료 이벤트 수신 - 배치 작업 등록: concertId={}, reservationId={}",
                event.getConcertId(), event.getReservationId());

        try {
            // 인기도 랭킹 업데이트를 위한 배치 작업 등록
            registerBatchJob(
                    event.getConcertId(),
                    RankingBatchJob.JobType.POPULARITY_UPDATE,
                    "예약 완료로 인한 인기도 랭킹 업데이트 필요"
            );

            log.info("인기도 랭킹 배치 작업 등록 완료: concertId={}", event.getConcertId());

        } catch (Exception e) {
            log.error("예약 완료 이벤트 처리 중 오류 발생: concertId={}", event.getConcertId(), e);
            // 배치 작업 등록 실패는 주요 비즈니스 로직에 영향주지 않도록 예외를 삼킴
        }
    }

    /**
     * 콘서트 매진 이벤트 처리
     *
     * 매진 속도 랭킹 업데이트를 위한 배치 작업 등록
     * 매진 시간, 총 좌석 수 등을 고려한 매진 속도 점수 계산 필요
     *
     * @param event 콘서트 매진 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleConcertSoldOut(ConcertSoldOutEvent event) {
        log.info("콘서트 매진 이벤트 수신 - 배치 작업 등록: concertId={}, duration={}분",
                event.getConcertId(), event.getSoldOutDurationMinutes());

        try {
            // 매진 속도 랭킹 업데이트를 위한 배치 작업 등록
            registerBatchJob(
                    event.getConcertId(),
                    RankingBatchJob.JobType.SOLDOUT_SPEED_UPDATE,
                    String.format("매진 완료 - 소요시간: %d분, 총좌석: %d석",
                            event.getSoldOutDurationMinutes(), event.getTotalSeats())
            );

            log.info("매진 속도 랭킹 배치 작업 등록 완료: concertId={}, duration={}분",
                    event.getConcertId(), event.getSoldOutDurationMinutes());

        } catch (Exception e) {
            log.error("콘서트 매진 이벤트 처리 중 오류 발생: concertId={}", event.getConcertId(), e);
            // 배치 작업 등록 실패는 주요 비즈니스 로직에 영향주지 않도록 예외를 삼킴
        }
    }

    /**
     * 랭킹 배치 작업을 등록
     *
     * 중복 작업 방지를 위해 동일한 콘서트의 동일한 작업 타입이 이미 대기 중인 경우
     * 기존 작업의 우선순위를 업데이트
     *
     * @param concertId 콘서트 ID
     * @param jobType 작업 타입 (인기도/매진속도)
     * @param description 작업 설명
     */
    private void registerBatchJob(Long concertId, RankingBatchJob.JobType jobType, String description) {
        // 기존에 대기 중인 동일한 작업이 있는지 확인
        RankingBatchJob existingJob = rankingBatchJobRepository
                .findByConcertIdAndJobTypeAndStatus(concertId, jobType, RankingBatchJob.JobStatus.PENDING)
                .orElse(null);

        if (existingJob != null) {
            // 기존 작업의 우선순위를 높이고 설명 업데이트
            existingJob.updatePriority();
            existingJob.updateDescription(description);
            rankingBatchJobRepository.save(existingJob);

            log.info("기존 배치 작업 우선순위 업데이트: jobId={}, concertId={}, type={}",
                    existingJob.getId(), concertId, jobType);
        } else {
            // 새로운 배치 작업 생성
            RankingBatchJob newJob = RankingBatchJob.builder()
                    .concertId(concertId)
                    .jobType(jobType)
                    .status(RankingBatchJob.JobStatus.PENDING)
                    .priority(1)
                    .description(description)
                    .createdAt(LocalDateTime.now())
                    .build();

            rankingBatchJobRepository.save(newJob);

            log.info("새로운 배치 작업 등록: concertId={}, type={}, description={}",
                    concertId, jobType, description);
        }
    }
}