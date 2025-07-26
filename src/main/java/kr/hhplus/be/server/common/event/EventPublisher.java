package kr.hhplus.be.server.common.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 이벤트 발행 (비동기)
     */
    public void publishEvent(String topic, BaseEvent event) {
        publishEvent(topic, null, event);
    }

    /**
     * 키가 있는 이벤트 발행 (파티션 라우팅용)
     */
    public void publishEvent(String topic, String key, BaseEvent event) {
        try {
            log.info("이벤트 발행 시작: topic={}, eventType={}, eventId={}",
                    topic, event.getEventType(), event.getEventId());

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("이벤트 발행 성공: topic={}, partition={}, offset={}, eventId={}",
                            topic, result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(), event.getEventId());
                } else {
                    log.error("이벤트 발행 실패: topic={}, eventId={}, error={}",
                            topic, event.getEventId(), ex.getMessage(), ex);
                }
            });

        } catch (Exception e) {
            log.error("이벤트 발행 중 예외 발생: topic={}, eventId={}",
                    topic, event.getEventId(), e);
        }
    }

    /**
     * 동기 이벤트 발행 (중요한 이벤트용)
     */
    public void publishEventSync(String topic, String key, BaseEvent event) {
        try {
            log.info("동기 이벤트 발행: topic={}, eventType={}, eventId={}",
                    topic, event.getEventType(), event.getEventId());

            SendResult<String, Object> result = kafkaTemplate.send(topic, key, event).get();

            log.info("동기 이벤트 발행 완료: topic={}, partition={}, offset={}, eventId={}",
                    topic, result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(), event.getEventId());

        } catch (Exception e) {
            log.error("동기 이벤트 발행 실패: topic={}, eventId={}",
                    topic, event.getEventId(), e);
            throw new RuntimeException("이벤트 발행 실패", e);
        }
    }
}
