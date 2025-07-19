package kr.hhplus.be.server.external.repository;

import kr.hhplus.be.server.external.entity.FailedDataTransfer;
import kr.hhplus.be.server.external.entity.FailedDataStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FailedDataRepository extends JpaRepository<FailedDataTransfer, Long> {

    // 재시도 대상 조회 (실패 상태이고 재시도 횟수가 일정 수 이하)
    List<FailedDataTransfer> findByStatusAndRetryCountLessThan(
            FailedDataStatus status, Integer maxRetryCount);

    // 특정 기간 내 실패 데이터 조회 (대량 오류 발생 시 범위 확인용)
    @Query("SELECT f FROM FailedDataTransfer f WHERE f.failedAt BETWEEN :startDate AND :endDate")
    List<FailedDataTransfer> findByFailedAtBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // 특정 예약 ID로 실패 데이터 조회
    List<FailedDataTransfer> findByReservationId(String reservationId);
}