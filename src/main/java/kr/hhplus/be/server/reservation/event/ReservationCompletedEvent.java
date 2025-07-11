package kr.hhplus.be.server.reservation.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReservationCompletedEvent {
    private final String reservationId;
    private final String userId;
    private final Long concertId;
    private final Long seatId;
    private final Integer seatNumber;
    private final BigDecimal price;
    private final LocalDateTime reservedAt;

    public ReservationCompletedEvent(String reservationId, String userId, Long concertId,
                                     Long seatId, Integer seatNumber, BigDecimal price, LocalDateTime reservedAt) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.concertId = concertId;
        this.seatId = seatId;
        this.seatNumber = seatNumber;
        this.price = price;
        this.reservedAt = reservedAt;
    }

    // getters
    public String getReservationId() { return reservationId; }
    public String getUserId() { return userId; }
    public Long getConcertId() { return concertId; }
    public Long getSeatId() { return seatId; }
    public Integer getSeatNumber() { return seatNumber; }
    public BigDecimal getPrice() { return price; }
    public LocalDateTime getReservedAt() { return reservedAt; }
}