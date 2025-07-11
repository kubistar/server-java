package kr.hhplus.be.server.reservation.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RecordApplicationEvents
class ReservationEventTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ApplicationEvents events;

    @Test
    void 예약_완료_이벤트가_정상적으로_발행된다() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        BigDecimal price = new BigDecimal("50000"); // ✅ BigDecimal 사용

        ReservationCompletedEvent event = new ReservationCompletedEvent(
                "reservation123",
                "user123",
                1L,
                100L,
                10,
                price,
                now
        );

        // When
        eventPublisher.publishEvent(event);

        // Then
        assertThat(events.stream(ReservationCompletedEvent.class)).hasSize(1);

        ReservationCompletedEvent publishedEvent = events.stream(ReservationCompletedEvent.class)
                .findFirst()
                .orElseThrow();

        assertThat(publishedEvent.getReservationId()).isEqualTo("reservation123");
        assertThat(publishedEvent.getUserId()).isEqualTo("user123");
        assertThat(publishedEvent.getConcertId()).isEqualTo(1L);
        assertThat(publishedEvent.getSeatNumber()).isEqualTo(10);
        assertThat(publishedEvent.getPrice()).isEqualTo(price);
    }
}