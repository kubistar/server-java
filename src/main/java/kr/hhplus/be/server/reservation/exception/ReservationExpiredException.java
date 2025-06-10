package kr.hhplus.be.server.reservation.exception;

import java.time.LocalDateTime;

public class ReservationExpiredException extends ReservationException {
    private final String reservationId;
    private final LocalDateTime expiredAt;

    public ReservationExpiredException(String reservationId, LocalDateTime expiredAt) {
        super(String.format("예약 %s가 %s에 만료되었습니다.", reservationId, expiredAt));
        this.reservationId = reservationId;
        this.expiredAt = expiredAt;
    }

    public String getReservationId() { return reservationId; }
    public LocalDateTime getExpiredAt() { return expiredAt; }
}