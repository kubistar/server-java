package kr.hhplus.be.server.common.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = kr.hhplus.be.server.balance.event.BalanceChargedEvent.class, name = "BALANCE_CHARGED"),
        @JsonSubTypes.Type(value = kr.hhplus.be.server.balance.event.BalanceDeductedEvent.class, name = "BALANCE_DEDUCTED"),
        @JsonSubTypes.Type(value = kr.hhplus.be.server.payment.event.PaymentCompletedEvent.class, name = "PAYMENT_COMPLETED"),
        @JsonSubTypes.Type(value = kr.hhplus.be.server.payment.event.PaymentFailedEvent.class, name = "PAYMENT_FAILED"),
        @JsonSubTypes.Type(value = kr.hhplus.be.server.reservation.event.ReservationCompletedEvent.class, name = "RESERVATION_COMPLETED"),
        @JsonSubTypes.Type(value = kr.hhplus.be.server.reservation.event.ReservationCancelledEvent.class, name = "RESERVATION_CANCELLED"),
        @JsonSubTypes.Type(value = kr.hhplus.be.server.concert.event.ConcertSoldOutEvent.class, name = "CONCERT_SOLD_OUT"),
        @JsonSubTypes.Type(value = kr.hhplus.be.server.queue.event.UserActivatedEvent.class, name = "USER_ACTIVATED"),
        @JsonSubTypes.Type(value = kr.hhplus.be.server.queue.event.TokenExpiredEvent.class, name = "TOKEN_EXPIRED")
})
@Getter
@NoArgsConstructor
public abstract class BaseEvent {

    protected String eventId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    protected LocalDateTime timestamp;

    protected String eventType;

    public BaseEvent(String eventType) {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.eventType = eventType;
    }
}