package kr.hhplus.be.server.reservation.exception;

public abstract class ReservationException extends RuntimeException {
    protected ReservationException(String message) {
        super(message);
    }

    protected ReservationException(String message, Throwable cause) {
        super(message, cause);
    }
}
