package kr.hhplus.be.server.ranking.listener;

import kr.hhplus.be.server.balance.event.BalanceChargedEvent;
import kr.hhplus.be.server.balance.event.BalanceDeductedEvent;
import kr.hhplus.be.server.payment.event.PaymentCompletedEvent;
import kr.hhplus.be.server.reservation.event.ReservationCompletedEvent;
import kr.hhplus.be.server.concert.event.ConcertSoldOutEvent;
import kr.hhplus.be.server.queue.event.UserActivatedEvent;
import kr.hhplus.be.server.ranking.service.ConcertRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RankingKafkaEventListener {

    private final ConcertRankingService concertRankingService;

    /**
     * 예약 완료 이벤트 처리 - 실시간 랭킹 업데이트
     */
    @KafkaListener(topics = "reservation-events", groupId = "ranking-service")
    public void handleReservationCompleted(@Payload ReservationCompletedEvent event,
                                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                           @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                           @Header(KafkaHeaders.OFFSET) long offset,
                                           Acknowledgment ack) {
        try {
            log.info("예약 완료 이벤트 수신 - concertId: {}, reservationId: {}, partition: {}, offset: {}",
                    event.getConcertId(), event.getReservationId(), partition, offset);

            // 실시간 랭킹 업데이트
            concertRankingService.updateBookingRanking(event.getConcertId());

            // 수동 커밋
            ack.acknowledge();

            log.info("예약 완료 이벤트 처리 완료 - concertId: {}", event.getConcertId());

        } catch (Exception e) {
            log.error("예약 완료 이벤트 처리 실패 - concertId: {}, error: {}",
                    event.getConcertId(), e.getMessage(), e);
            // 예외 발생 시에도 ack를 호출하여 무한 재시도 방지
            // 실제 운영에서는 DLQ(Dead Letter Queue) 고려
            ack.acknowledge();
        }
    }

    /**
     * 콘서트 매진 이벤트 처리 - 매진 속도 랭킹 업데이트
     */
    @KafkaListener(topics = "concert-events", groupId = "ranking-service")
    public void handleConcertSoldOut(@Payload ConcertSoldOutEvent event,
                                     @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                     @Header(KafkaHeaders.OFFSET) long offset,
                                     Acknowledgment ack) {
        try {
            log.info("콘서트 매진 이벤트 수신 - concertId: {}, duration: {}분, partition: {}, offset: {}",
                    event.getConcertId(), event.getSoldOutDurationMinutes(), partition, offset);

            // 매진 속도 랭킹 업데이트
            concertRankingService.updateSoldOutRanking(
                    event.getConcertId(),
                    event.getBookingStartTime(),
                    event.getSoldOutTime()
            );

            ack.acknowledge();

            log.info("콘서트 매진 이벤트 처리 완료 - concertId: {}", event.getConcertId());

        } catch (Exception e) {
            log.error("콘서트 매진 이벤트 처리 실패 - concertId: {}, error: {}",
                    event.getConcertId(), e.getMessage(), e);
            ack.acknowledge();
        }
    }

    /**
     * 결제 완료 이벤트 처리 - 추가 랭킹 로직
     */
    @KafkaListener(topics = "payment-events", groupId = "ranking-service")
    public void handlePaymentCompleted(@Payload PaymentCompletedEvent event,
                                       Acknowledgment ack) {
        try {
            log.info("결제 완료 이벤트 수신 - paymentId: {}, userId: {}, amount: {}",
                    event.getPaymentId(), event.getUserId(), event.getAmount());

            // 결제 기반 추가 통계 업데이트 로직 (필요시)
            // concertRankingService.updatePaymentStatistics(event);

            ack.acknowledge();

        } catch (Exception e) {
            log.error("결제 완료 이벤트 처리 실패 - paymentId: {}", event.getPaymentId(), e);
            ack.acknowledge();
        }
    }

    /**
     * 사용자 활성화 이벤트 처리 - 대기열 통계
     */
    @KafkaListener(topics = "queue-events", groupId = "ranking-service")
    public void handleUserActivated(@Payload UserActivatedEvent event,
                                    Acknowledgment ack) {
        try {
            log.info("사용자 활성화 이벤트 수신 - userId: {}, previousPosition: {}",
                    event.getUserId(), event.getPreviousPosition());

            // 대기열 통계 업데이트 로직 (필요시)
            // queueStatisticsService.updateActivationStats(event);

            ack.acknowledge();

        } catch (Exception e) {
            log.error("사용자 활성화 이벤트 처리 실패 - userId: {}", event.getUserId(), e);
            ack.acknowledge();
        }
    }
}