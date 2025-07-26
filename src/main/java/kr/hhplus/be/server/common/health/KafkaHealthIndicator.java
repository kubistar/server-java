package kr.hhplus.be.server.common.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public Health health() {
        try {
            // Kafka 연결 상태 확인
            kafkaTemplate.getProducerFactory().createProducer().metrics();

            return Health.up()
                    .withDetail("kafka", "UP")
                    .withDetail("status", "Kafka is reachable")
                    .build();

        } catch (Exception e) {
            log.error("Kafka health check failed", e);

            return Health.down()
                    .withDetail("kafka", "DOWN")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
