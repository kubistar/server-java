package kr.hhplus.be.server.exception;

public class ConcurrentReservationException extends ReservationException {
    private final int retryAfterSeconds;

    public ConcurrentReservationException(int retryAfterSeconds) {
        super("이미 선택된 좌석입니다.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() { return retryAfterSeconds; }
}