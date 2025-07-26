package kr.hhplus.be.server.notification.listener;

import kr.hhplus.be.server.balance.event.BalanceChargedEvent;
import kr.hhplus.be.server.payment.event.PaymentCompletedEvent;
import kr.hhplus.be.server.payment.event.PaymentFailedEvent;
import kr.hhplus.be.server.reservation.event.ReservationCompletedEvent;
import kr.hhplus.be.server.reservation.event.ReservationCancelledEvent;
import kr.hhplus.be.server.queue.event.UserActivatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    // private final NotificationService notificationService; // 알림 서비스 구현 시

    /**
     * 결제 완료 알림
     */
    @KafkaListener(topics = "payment-events", groupId = "notification-service")
    public void handlePaymentCompleted(PaymentCompletedEvent event, Acknowledgment ack) {
        try {
            log.info("결제 완료 알림 발송 - userId: {}, amount: {}",
                    event.getUserId(), event.getAmount());

            // 결제 완료 알림 발송
            sendNotification(event.getUserId(),
                    "결제가 완료되었습니다",
                    String.format("결제 금액: %s원", event.getAmount()));

            ack.acknowledge();

        } catch (Exception e) {
            log.error("결제 완료 알림 발송 실패", e);
            ack.acknowledge();
        }
    }

    /**
     * 예약 완료 알림
     */
    @KafkaListener(topics = "reservation-events", groupId = "notification-service")
    public void handleReservationCompleted(ReservationCompletedEvent event, Acknowledgment ack) {
        try {
            log.info("예약 완료 알림 발송 - userId: {}, concertId: {}",
                    event.getUserId(), event.getConcertId());

            // 예약 완료 알림 발송
            sendNotification(event.getUserId(),
                    "예약이 완료되었습니다",
                    String.format("좌석 번호: %d번", event.getSeatNumber()));

            ack.acknowledge();

        } catch (Exception e) {
            log.error("예약 완료 알림 발송 실패", e);
            ack.acknowledge();
        }
    }

    /**
     * 대기열 활성화 알림
     */
    @KafkaListener(topics = "queue-events", groupId = "notification-service")
    public void handleUserActivated(UserActivatedEvent event, Acknowledgment ack) {
        try {
            log.info("대기열 활성화 알림 발송 - userId: {}", event.getUserId());

            // 대기열 활성화 알림 발송
            sendNotification(event.getUserId(),
                    "대기열 순서가 되었습니다",
                    "지금 예약을 진행하세요!");

            ack.acknowledge();

        } catch (Exception e) {
            log.error("대기열 활성화 알림 발송 실패", e);
            ack.acknowledge();
        }
    }

    /**
     * 잔액 충전 알림
     */
    @KafkaListener(topics = "balance-events", groupId = "notification-service")
    public void handleBalanceCharged(BalanceChargedEvent event, Acknowledgment ack) {
        try {
            log.info("잔액 충전 알림 발송 - userId: {}, amount: {}",
                    event.getUserId(), event.getChargedAmount());

            // 잔액 충전 알림 발송
            sendNotification(event.getUserId(),
                    "잔액 충전이 완료되었습니다",
                    String.format("충전 금액: %s원, 현재 잔액: %s원",
                            event.getChargedAmount(), event.getBalanceAfter()));

            ack.acknowledge();

        } catch (Exception e) {
            log.error("잔액 충전 알림 발송 실패", e);
            ack.acknowledge();
        }
    }

    private void sendNotification(String userId, String title, String message) {
        // 실제 알림 발송 로직 구현
        log.info("알림 발송: userId={}, title={}, message={}", userId, title, message);
    }
}