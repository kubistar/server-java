package kr.hhplus.be.server.ranking.dto;

/**
 * 예약 속도 응답 DTO
 */
public class BookingSpeedResponse {
    private final Long concertId;
    private final double bookingSpeedPerMinute;
    private final double bookingSpeedPerHour;

    public BookingSpeedResponse(Long concertId, double bookingSpeedPerMinute, double bookingSpeedPerHour) {
        this.concertId = concertId;
        this.bookingSpeedPerMinute = bookingSpeedPerMinute;
        this.bookingSpeedPerHour = bookingSpeedPerHour;
    }

    public static BookingSpeedResponseBuilder builder() {
        return new BookingSpeedResponseBuilder();
    }

    // Getters
    public Long getConcertId() { return concertId; }
    public double getBookingSpeedPerMinute() { return bookingSpeedPerMinute; }
    public double getBookingSpeedPerHour() { return bookingSpeedPerHour; }

    public static class BookingSpeedResponseBuilder {
        private Long concertId;
        private double bookingSpeedPerMinute;
        private double bookingSpeedPerHour;

        public BookingSpeedResponseBuilder concertId(Long concertId) {
            this.concertId = concertId;
            return this;
        }

        public BookingSpeedResponseBuilder bookingSpeedPerMinute(double bookingSpeedPerMinute) {
            this.bookingSpeedPerMinute = bookingSpeedPerMinute;
            return this;
        }

        public BookingSpeedResponseBuilder bookingSpeedPerHour(double bookingSpeedPerHour) {
            this.bookingSpeedPerHour = bookingSpeedPerHour;
            return this;
        }

        public BookingSpeedResponse build() {
            return new BookingSpeedResponse(concertId, bookingSpeedPerMinute, bookingSpeedPerHour);
        }
    }
}