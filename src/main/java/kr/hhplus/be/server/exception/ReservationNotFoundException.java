package kr.hhplus.be.server.exception;

public class ReservationNotFoundException extends ReservationException {
    private final String reservationId;

    public ReservationNotFoundException(String reservationId) {
        super(String.format("예약 ID %s를 찾을 수 없습니다.", reservationId));
        this.reservationId = reservationId;
    }

    public String getReservationId() { return reservationId; }
}