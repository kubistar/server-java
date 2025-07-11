package kr.hhplus.be.server.external.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReservationDataDto {
    private final String reservationId;
    private final String userId;
    private final Long concertId;
    private final Integer seatNumber;
    private final BigDecimal price;
    private final LocalDateTime reservedAt;

    public ReservationDataDto(String reservationId, String userId, Long concertId,
                              Integer seatNumber, BigDecimal price, LocalDateTime reservedAt) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.concertId = concertId;
        this.seatNumber = seatNumber;
        this.price = price;
        this.reservedAt = reservedAt;
    }

    // getters
    public String getReservationId() { return reservationId; }
    public String getUserId() { return userId; }
    public Long getConcertId() { return concertId; }
    public Integer getSeatNumber() { return seatNumber; }
    public BigDecimal getPrice() { return price; }
    public LocalDateTime getReservedAt() { return reservedAt; }
}