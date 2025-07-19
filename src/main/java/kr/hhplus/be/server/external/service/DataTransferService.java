package kr.hhplus.be.server.external.service;

import kr.hhplus.be.server.external.client.DataPlatformClient;
import kr.hhplus.be.server.external.dto.ReservationDataDto;
import kr.hhplus.be.server.external.entity.FailedDataStatus;
import kr.hhplus.be.server.external.entity.FailedDataTransfer;
import kr.hhplus.be.server.external.repository.FailedDataRepository;
import kr.hhplus.be.server.reservation.event.ReservationCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Service
public class DataTransferService {

    private static final Logger logger = LoggerFactory.getLogger(DataTransferService.class);

    private final DataPlatformClient dataPlatformClient;
    private final FailedDataRepository failedDataRepository;

    public DataTransferService(DataPlatformClient dataPlatformClient,
                               FailedDataRepository failedDataRepository) {
        this.dataPlatformClient = dataPlatformClient;
        this.failedDataRepository = failedDataRepository;
    }

    /**
     * 예약 완료 이벤트를 받아서 데이터 플랫폼으로 전송
     * 트랜잭션 완료 후 비동기로 처리되며, 실패 시 재시도를 위해 실패 데이터를 저장
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReservationCompleted(ReservationCompletedEvent event) {
        logger.info("예약 완료 데이터 전송 시작 - reservationId: {}, userId: {}",
                event.getReservationId(), event.getUserId());

        try {
            ReservationDataDto data = new ReservationDataDto(
                    event.getReservationId(),
                    event.getUserId(),
                    event.getConcertId(),
                    event.getSeatNumber(),
                    event.getPrice(),
                    event.getReservedAt()
            );

            // 데이터 플랫폼으로 전송
            dataPlatformClient.sendReservationData(data);

            logger.info("예약 데이터 전송 성공 - reservationId: {}", event.getReservationId());

        } catch (Exception e) {
            // 실패한 데이터를 DB에 저장하여 나중에 재시도할 수 있도록 함
            logger.error("예약 데이터 전송 실패 - reservationId: {}, userId: {}, error: {}",
                    event.getReservationId(), event.getUserId(), e.getMessage(), e);

            // 실패 데이터 저장
            saveFailedData(event, e.getMessage());
        }
    }

    /**
     * 실패한 데이터를 DB에 저장
     * 대량 오류 발생 시 언제부터 언제까지 실패했는지 추적 가능
     */
    private void saveFailedData(ReservationCompletedEvent event, String errorMessage) {
        try {
            FailedDataTransfer failedData = FailedDataTransfer.builder()
                    .reservationId(event.getReservationId())
                    .userId(event.getUserId())
                    .concertId(event.getConcertId())
                    .seatNumber(event.getSeatNumber())
                    .price(event.getPrice())
                    .reservedAt(event.getReservedAt())
                    .failedAt(LocalDateTime.now())
                    .errorMessage(errorMessage)
                    .retryCount(0)
                    .status(FailedDataStatus.FAILED)
                    .build();

            failedDataRepository.save(failedData);

            logger.info("실패 데이터 저장 완료 - reservationId: {}", event.getReservationId());

        } catch (Exception e) {
            // 실패 데이터 저장마저 실패한 경우 - 이건 정말 심각한 상황
            logger.error("실패 데이터 저장 중 오류 발생 - reservationId: {}, error: {}",
                    event.getReservationId(), e.getMessage(), e);
        }
    }
}