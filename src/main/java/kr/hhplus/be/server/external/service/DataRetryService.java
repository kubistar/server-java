package kr.hhplus.be.server.external.service;

import kr.hhplus.be.server.external.client.DataPlatformClient;
import kr.hhplus.be.server.external.dto.ReservationDataDto;
import kr.hhplus.be.server.external.entity.FailedDataTransfer;
import kr.hhplus.be.server.external.entity.FailedDataStatus;
import kr.hhplus.be.server.external.repository.FailedDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DataRetryService {

    private static final Logger logger = LoggerFactory.getLogger(DataRetryService.class);
    private static final int MAX_RETRY_COUNT = 3; // 최대 재시도 횟수

    private final DataPlatformClient dataPlatformClient;
    private final FailedDataRepository failedDataRepository;

    public DataRetryService(DataPlatformClient dataPlatformClient,
                            FailedDataRepository failedDataRepository) {
        this.dataPlatformClient = dataPlatformClient;
        this.failedDataRepository = failedDataRepository;
    }

    /**
     * 매 10분마다 실패한 데이터 재시도
     * 실제 운영환경에서는 cron 표현식으로 적절한 시간대에 실행하도록 설정
     */
    @Scheduled(fixedRate = 600000) // 10분마다 실행
    @Transactional
    public void retryFailedDataTransfer() {
        logger.info("실패 데이터 재시도 배치 시작");

        // 재시도 대상 조회
        List<FailedDataTransfer> failedDataList = failedDataRepository
                .findByStatusAndRetryCountLessThan(FailedDataStatus.FAILED, MAX_RETRY_COUNT);

        if (failedDataList.isEmpty()) {
            logger.info("재시도할 실패 데이터가 없습니다.");
            return;
        }

        logger.info("재시도 대상 데이터 {}건 발견", failedDataList.size());

        int successCount = 0;
        int failCount = 0;

        for (FailedDataTransfer failedData : failedDataList) {
            try {
                // 재시도 상태로 변경
                failedData.updateStatus(FailedDataStatus.RETRYING);
                failedData.incrementRetryCount();
                failedDataRepository.save(failedData);

                logger.info("데이터 재시도 중 - reservationId: {}, 재시도 횟수: {}",
                        failedData.getReservationId(), failedData.getRetryCount());

                // 데이터 전송 시도
                ReservationDataDto data = new ReservationDataDto(
                        failedData.getReservationId(),
                        failedData.getUserId(),
                        failedData.getConcertId(),
                        failedData.getSeatNumber(),
                        failedData.getPrice(),
                        failedData.getReservedAt()
                );

                dataPlatformClient.sendReservationData(data);

                // 성공 시 상태 변경
                failedData.updateStatus(FailedDataStatus.SUCCESS);
                failedDataRepository.save(failedData);

                successCount++;
                logger.info("데이터 재시도 성공 - reservationId: {}", failedData.getReservationId());

            } catch (Exception e) {
                logger.error("데이터 재시도 실패 - reservationId: {}, 재시도 횟수: {}, error: {}",
                        failedData.getReservationId(), failedData.getRetryCount(),
                        e.getMessage(), e);

                // 최대 재시도 횟수 초과 시 포기 상태로 변경
                if (failedData.getRetryCount() >= MAX_RETRY_COUNT) {
                    failedData.updateStatus(FailedDataStatus.ABANDONED);
                    logger.warn("최대 재시도 횟수 초과, 포기 처리 - reservationId: {}",
                            failedData.getReservationId());
                } else {
                    failedData.updateStatus(FailedDataStatus.FAILED);
                }

                failedDataRepository.save(failedData);
                failCount++;
            }
        }

        logger.info("실패 데이터 재시도 배치 완료 - 성공: {}건, 실패: {}건", successCount, failCount);
    }

    /**
     * 특정 기간의 실패 데이터를 수동으로 재시도하는 메서드
     * 대량 오류 발생 시 관리자가 호출할 수 있도록 제공
     */
    @Transactional
    public void retryFailedDataByPeriod(String startDate, String endDate) {
        logger.info("기간별 실패 데이터 재시도 시작 - {} ~ {}", startDate, endDate);

        // 실제 구현에서는 날짜 파싱과 검증 로직 추가 필요
        // 여기서는 개념적인 코드만 제공

        // LocalDateTime start = LocalDateTime.parse(startDate);
        // LocalDateTime end = LocalDateTime.parse(endDate);
        // List<FailedDataTransfer> failedDataList = failedDataRepository.findByFailedAtBetween(start, end);

        // 위의 retryFailedDataTransfer() 로직과 동일하게 처리

        logger.info("기간별 실패 데이터 재시도 완료");
    }
}