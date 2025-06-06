package kr.hhplus.be.server.exception;

public class SeatNotAvailableException extends ReservationException {
    private final Long concertId;
    private final Integer seatNumber;
    private final String currentStatus;

    public SeatNotAvailableException(Long concertId, Integer seatNumber, String currentStatus) {
        super(String.format("좌석 %d번은 현재 예약할 수 없습니다. 현재 상태: %s", seatNumber, currentStatus));
        this.concertId = concertId;
        this.seatNumber = seatNumber;
        this.currentStatus = currentStatus;
    }

    public Long getConcertId() { return concertId; }
    public Integer getSeatNumber() { return seatNumber; }
    public String getCurrentStatus() { return currentStatus; }
}
