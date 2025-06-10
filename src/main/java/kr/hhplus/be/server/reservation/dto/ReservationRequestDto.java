package kr.hhplus.be.server.reservation.dto;

public class ReservationRequestDto {
    private Long concertId;
    private Integer seatNumber;
    private String userId;

    // 생성자, getter, setter
    public ReservationRequestDto() {}

    public Long getConcertId() { return concertId; }
    public void setConcertId(Long concertId) { this.concertId = concertId; }

    public Integer getSeatNumber() { return seatNumber; }
    public void setSeatNumber(Integer seatNumber) { this.seatNumber = seatNumber; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}