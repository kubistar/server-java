package kr.hhplus.be.server.ranking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 랭킹 배치 작업 엔티티
 *
 * 랭킹 업데이트가 필요한 작업들을 큐 형태로 관리
 * 우선순위 기반으로 배치 처리되어 Redis 랭킹 데이터 갱신
 */
@Entity
@Table(name = "ranking_batch_job")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankingBatchJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 랭킹 업데이트 대상 콘서트 ID
     */
    @Column(nullable = false)
    private Long concertId;

    /**
     * 배치 작업 타입
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType jobType;

    /**
     * 작업 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    /**
     * 작업 우선순위 (높을수록 먼저 처리)
     */
    @Column(nullable = false)
    private Integer priority;

    /**
     * 작업 설명
     */
    @Column(length = 500)
    private String description;

    /**
     * 재시도 횟수
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 마지막 오류 메시지
     */
    @Column(length = 1000)
    private String lastError;

    /**
     * 작업 생성 시간
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * 작업 시작 시간
     */
    private LocalDateTime startedAt;

    /**
     * 작업 완료 시간
     */
    private LocalDateTime completedAt;

    /**
     * 작업 타입 열거형
     */
    public enum JobType {
        /**
         * 인기도 랭킹 업데이트
         * 예약 수 기반으로 콘서트 인기도 순위 갱신
         */
        POPULARITY_UPDATE,

        /**
         * 매진 속도 랭킹 업데이트
         * 매진 소요 시간 기반으로 매진 속도 순위 갱신
         */
        SOLDOUT_SPEED_UPDATE
    }

    /**
     * 작업 상태 열거형
     */
    public enum JobStatus {
        /**
         * 대기 중 - 배치 처리 대기 상태
         */
        PENDING,

        /**
         * 처리 중 - 현재 배치 작업 실행 중
         */
        PROCESSING,

        /**
         * 완료 - 배치 작업 성공적으로 완료
         */
        COMPLETED,

        /**
         * 실패 - 배치 작업 실행 실패 (재시도 가능)
         */
        FAILED,

        /**
         * 중단 - 최대 재시도 횟수 초과로 작업 중단
         */
        ABANDONED
    }

    /**
     * 작업 우선순위 증가
     * 동일한 콘서트에 대한 중복 작업 요청 시 우선순위를 높여 빠른 처리 유도
     */
    public void updatePriority() {
        this.priority = this.priority + 1;
    }

    /**
     * 작업 설명 업데이트
     *
     * @param newDescription 새로운 작업 설명
     */
    public void updateDescription(String newDescription) {
        this.description = newDescription;
    }

    /**
     * 작업 시작 처리
     * 상태를 PROCESSING으로 변경하고 시작 시간 기록
     */
    public void startProcessing() {
        this.status = JobStatus.PROCESSING;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * 작업 완료 처리
     * 상태를 COMPLETED로 변경하고 완료 시간 기록
     */
    public void completeProcessing() {
        this.status = JobStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 작업 실패 처리
     * 상태를 FAILED로 변경하고 재시도 횟수 증가, 오류 메시지 기록
     *
     * @param errorMessage 오류 메시지
     */
    public void failProcessing(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.retryCount = this.retryCount + 1;
        this.lastError = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 작업 중단 처리
     * 최대 재시도 횟수 초과 시 작업을 완전히 중단
     */
    public void abandon() {
        this.status = JobStatus.ABANDONED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 작업 재시도를 위한 상태 초기화
     * FAILED 상태에서 다시 PENDING으로 변경하여 재처리 가능하도록 함
     */
    public void resetForRetry() {
        if (this.status == JobStatus.FAILED) {
            this.status = JobStatus.PENDING;
            this.startedAt = null;
            this.completedAt = null;
        }
    }
}