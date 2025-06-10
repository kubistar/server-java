package kr.hhplus.be.server.reservation.dto;

import kr.hhplus.be.server.reservation.domain.Reservation;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ReservationResult {
    private final String reservationId;
    private final Long seatId;
    private final Long concertId;
    private final Integer seatNumber;
    private final String userId;
    private final Integer price;
    private final LocalDateTime expiresAt;
    private final long remainingTimeSeconds;

    public ReservationResult(Reservation reservation, Integer seatNumber) {
        this.reservationId = reservation.getReservationId();
        this.seatId = reservation.getSeatId();
        this.concertId = reservation.getConcertId();
        this.seatNumber = seatNumber;
        this.userId = reservation.getUserId();
        this.price = reservation.getPrice();
        this.expiresAt = reservation.getExpiresAt();
        this.remainingTimeSeconds = reservation.getRemainingTimeSeconds();
    }

    // Getter 메소드들

}