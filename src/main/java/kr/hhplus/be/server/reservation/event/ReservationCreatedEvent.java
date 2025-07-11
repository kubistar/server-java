package kr.hhplus.be.server.reservation.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ReservationCreatedEvent {
    private final Long reservationId;
    private final Long concertId;
    private final Long seatId;
    private final String userId;
    private final LocalDateTime createdAt;

    public static ReservationCreatedEvent from(Long reservationId, Long concertId, Long seatId, String userId) {
        return new ReservationCreatedEvent(reservationId, concertId, seatId, userId, LocalDateTime.now());
    }
}