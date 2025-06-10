package kr.hhplus.be.server.reservation.scheduler;

import kr.hhplus.be.server.reservation.service.ReserveSeatUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ReservationScheduler.class);

    private final ReserveSeatUseCase reserveSeatUseCase;

    public ReservationScheduler(ReserveSeatUseCase reserveSeatUseCase) {
        this.reserveSeatUseCase = reserveSeatUseCase;
    }

    @Scheduled(fixedDelay = 30000) // 30초마다 실행
    public void releaseExpiredReservations() {
        try {
            logger.info("만료된 예약 해제 스케줄러 시작");

            long startTime = System.currentTimeMillis();
            reserveSeatUseCase.releaseExpiredReservations();
            long endTime = System.currentTimeMillis();

            logger.info("만료된 예약 해제 완료. 소요 시간: {}ms", endTime - startTime);

        } catch (Exception e) {
            logger.error("만료된 예약 해제 중 오류 발생", e);
        }
    }
}