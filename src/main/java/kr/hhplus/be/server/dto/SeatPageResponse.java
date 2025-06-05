package kr.hhplus.be.server.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 좌석 페이지 응답 DTO
 */
public class SeatPageResponse {
    private Long concertId;
    private String concertTitle;
    private LocalDate concertDate;
    private LocalTime concertTime;
    private String venue;
    private List<SeatResponseDto> seats;
    private SeatSummary summary;

    protected SeatPageResponse() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SeatPageResponse response = new SeatPageResponse();

        public Builder concertId(Long concertId) {
            response.concertId = concertId;
            return this;
        }

        public Builder concertTitle(String concertTitle) {
            response.concertTitle = concertTitle;
            return this;
        }

        public Builder concertDate(LocalDate concertDate) {
            response.concertDate = concertDate;
            return this;
        }

        public Builder concertTime(LocalTime concertTime) {
            response.concertTime = concertTime;
            return this;
        }

        public Builder venue(String venue) {
            response.venue = venue;
            return this;
        }

        public Builder seats(List<SeatResponseDto> seats) {
            response.seats = seats;
            return this;
        }

        public Builder summary(SeatSummary summary) {
            response.summary = summary;
            return this;
        }

        public SeatPageResponse build() {
            return response;
        }
    }

    // Getters
    public Long getConcertId() { return concertId; }
    public String getConcertTitle() { return concertTitle; }
    public LocalDate getConcertDate() { return concertDate; }
    public LocalTime getConcertTime() { return concertTime; }
    public String getVenue() { return venue; }
    public List<SeatResponseDto> getSeats() { return seats; }
    public SeatSummary getSummary() { return summary; }

    /**
     * 좌석 요약 정보
     */
    public static class SeatSummary {
        private Integer totalSeats;
        private Integer availableSeats;
        private Integer temporarilyAssignedSeats;
        private Integer reservedSeats;

        public SeatSummary(Integer totalSeats, Integer availableSeats,
                           Integer temporarilyAssignedSeats, Integer reservedSeats) {
            this.totalSeats = totalSeats;
            this.availableSeats = availableSeats;
            this.temporarilyAssignedSeats = temporarilyAssignedSeats;
            this.reservedSeats = reservedSeats;
        }

        // Getters
        public Integer getTotalSeats() { return totalSeats; }
        public Integer getAvailableSeats() { return availableSeats; }
        public Integer getTemporarilyAssignedSeats() { return temporarilyAssignedSeats; }
        public Integer getReservedSeats() { return reservedSeats; }
    }
}