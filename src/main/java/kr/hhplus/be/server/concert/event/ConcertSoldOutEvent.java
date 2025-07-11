package kr.hhplus.be.server.concert.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ConcertSoldOutEvent {
    private final Long concertId;
    private final String concertName;
    private final LocalDateTime bookingStartTime;
    private final LocalDateTime soldOutTime;
    private final int totalSeats;
    private final long soldOutDurationMinutes;

    public static ConcertSoldOutEvent create(Long concertId, String concertName,
                                             LocalDateTime bookingStartTime, LocalDateTime soldOutTime,
                                             int totalSeats) {
        long duration = java.time.Duration.between(bookingStartTime, soldOutTime).toMinutes();
        return new ConcertSoldOutEvent(concertId, concertName, bookingStartTime, soldOutTime, totalSeats, duration);
    }
}