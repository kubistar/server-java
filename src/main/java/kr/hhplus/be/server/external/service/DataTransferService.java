package kr.hhplus.be.server.external.service;

import kr.hhplus.be.server.external.client.DataPlatformClient;
import kr.hhplus.be.server.external.dto.ReservationDataDto;
import kr.hhplus.be.server.reservation.event.ReservationCompletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataTransferService {

    private final DataPlatformClient dataPlatformClient;

    public DataTransferService(DataPlatformClient dataPlatformClient) {
        this.dataPlatformClient = dataPlatformClient;
    }

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleReservationCompleted(ReservationCompletedEvent event) {
        try {
            ReservationDataDto data = new ReservationDataDto(
                    event.getReservationId(),
                    event.getUserId(),
                    event.getConcertId(),
                    event.getSeatNumber(),
                    event.getPrice(),
                    event.getReservedAt()
            );

            dataPlatformClient.sendReservationData(data);

        } catch (Exception e) {
            // 실패 시 재시도 로직이나 데드레터 큐 처리
            // 여기서는 로깅만 처리
            System.err.println("데이터 전송 실패: " + e.getMessage());
        }
    }
}