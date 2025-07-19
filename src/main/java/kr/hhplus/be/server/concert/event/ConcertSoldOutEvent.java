package kr.hhplus.be.server.concert.event;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Builder
public class ConcertSoldOutEvent {
    private final Long concertId;
    private final String concertName;
    private final LocalDateTime bookingStartTime;
    private final LocalDateTime soldOutTime;
    private final int totalSeats;
    private final long soldOutDurationMinutes;

    /**
     * 편의 메소드: 매진 소요 시간을 자동 계산하여 이벤트 생성
     */
    public static ConcertSoldOutEvent createWithDuration(Long concertId, String concertName,
                                                         LocalDateTime bookingStartTime,
                                                         LocalDateTime soldOutTime,
                                                         int totalSeats) {
        long duration = Duration.between(bookingStartTime, soldOutTime).toMinutes();

        return ConcertSoldOutEvent.builder()
                .concertId(concertId)
                .concertName(concertName)
                .bookingStartTime(bookingStartTime)
                .soldOutTime(soldOutTime)
                .totalSeats(totalSeats)
                .soldOutDurationMinutes(duration)
                .build();
    }
}