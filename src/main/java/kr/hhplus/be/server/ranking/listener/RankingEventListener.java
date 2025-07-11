package kr.hhplus.be.server.ranking.listener;

import kr.hhplus.be.server.ranking.service.ConcertRankingService;
import kr.hhplus.be.server.reservation.event.ReservationCreatedEvent;
import kr.hhplus.be.server.concert.event.ConcertSoldOutEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RankingEventListener {

    private final ConcertRankingService rankingService;

    /**
     * 예약 생성 이벤트 처리
     */
    @EventListener
    public void handleReservationCreated(ReservationCreatedEvent event) {
        try {
            log.debug("예약 생성 이벤트 수신 - concertId: {}", event.getConcertId());

            // 실시간 랭킹 업데이트
            rankingService.updateBookingRanking(event.getConcertId());

        } catch (Exception e) {
            log.error("예약 생성 이벤트 처리 중 오류 발생 - concertId: {}", event.getConcertId(), e);
        }
    }

    /**
     * 콘서트 매진 이벤트 처리
     */
    @EventListener
    public void handleConcertSoldOut(ConcertSoldOutEvent event) {
        try {
            log.info("콘서트 매진 이벤트 수신 - concertId: {}, 매진시간: {}",
                    event.getConcertId(), event.getSoldOutTime());

            // 매진 속도 랭킹 업데이트
            rankingService.updateSoldOutRanking(
                    event.getConcertId(),
                    event.getBookingStartTime(),
                    event.getSoldOutTime()
            );

            // 콘서트 상태 업데이트
            rankingService.updateConcertStatus(event.getConcertId(), "SOLD_OUT");

        } catch (Exception e) {
            log.error("매진 이벤트 처리 중 오류 발생 - concertId: {}", event.getConcertId(), e);
        }
    }
}