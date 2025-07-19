package kr.hhplus.be.server.ranking.repository;

import kr.hhplus.be.server.ranking.entity.RankingBatchJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 랭킹 배치 작업 리포지토리
 */
public interface RankingBatchJobRepository extends JpaRepository<RankingBatchJob, Long> {

    /**
     * 특정 상태의 배치 작업들을 우선순위와 생성시간 순으로 조회
     *
     * @param status 조회할 작업 상태
     * @param limit 최대 조회 개수
     * @return 우선순위 높은 순, 생성시간 빠른 순으로 정렬된 작업 목록
     */
    @Query("SELECT j FROM RankingBatchJob j WHERE j.status = :status " +
            "ORDER BY j.priority DESC, j.createdAt ASC")
    List<RankingBatchJob> findByStatusOrderByPriorityDescCreatedAtAsc(
            @Param("status") RankingBatchJob.JobStatus status,
            @Param("limit") int limit);

    /**
     * 콘서트 ID, 작업 타입, 상태로 배치 작업 조회 (중복 방지용)
     *
     * @param concertId 콘서트 ID
     * @param jobType 작업 타입
     * @param status 작업 상태
     * @return 해당 조건의 배치 작업 (있는 경우)
     */
    Optional<RankingBatchJob> findByConcertIdAndJobTypeAndStatus(
            Long concertId,
            RankingBatchJob.JobType jobType,
            RankingBatchJob.JobStatus status);

    /**
     * 특정 기간 내 완료된 배치 작업 조회
     *
     * @param startTime 조회 시작 시간
     * @param endTime 조회 종료 시간
     * @return 해당 기간 내 완료된 작업 목록
     */
    @Query("SELECT j FROM RankingBatchJob j WHERE j.status = 'COMPLETED' " +
            "AND j.completedAt BETWEEN :startTime AND :endTime " +
            "ORDER BY j.completedAt DESC")
    List<RankingBatchJob> findCompletedJobsBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 실패한 배치 작업 중 재시도 가능한 작업들 조회
     *
     * @param maxRetryCount 최대 재시도 횟수
     * @return 재시도 가능한 실패 작업 목록
     */
    @Query("SELECT j FROM RankingBatchJob j WHERE j.status = 'FAILED' " +
            "AND j.retryCount < :maxRetryCount " +
            "ORDER BY j.priority DESC, j.createdAt ASC")
    List<RankingBatchJob> findRetryableFailedJobs(@Param("maxRetryCount") int maxRetryCount);

    /**
     * 특정 콘서트의 모든 배치 작업 조회
     *
     * @param concertId 콘서트 ID
     * @return 해당 콘서트의 모든 배치 작업 목록
     */
    List<RankingBatchJob> findByConcertIdOrderByCreatedAtDesc(Long concertId);

    /**
     * 오래된 완료/중단 작업들 조회 (정리용)
     *
     * @param cutoffTime 기준 시간 (이 시간보다 이전에 완료된 작업들)
     * @return 정리 대상 작업 목록
     */
    @Query("SELECT j FROM RankingBatchJob j WHERE j.status IN ('COMPLETED', 'ABANDONED') " +
            "AND j.completedAt < :cutoffTime")
    List<RankingBatchJob> findOldCompletedJobs(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 콘서트별 작업 통계 조회
     *
     * @return [콘서트ID, 총작업수, 성공수, 실패수] 형태의 통계 데이터
     */
    @Query("SELECT j.concertId, COUNT(j), " +
            "SUM(CASE WHEN j.status = 'COMPLETED' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN j.status = 'FAILED' OR j.status = 'ABANDONED' THEN 1 ELSE 0 END) " +
            "FROM RankingBatchJob j " +
            "GROUP BY j.concertId " +
            "ORDER BY COUNT(j) DESC")
    List<Object[]> getJobStatisticsByConcert();
}