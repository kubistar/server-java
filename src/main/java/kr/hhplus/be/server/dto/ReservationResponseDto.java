package kr.hhplus.be.server.dto;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ReservationResponseDto {
    private String reservationId;
    private Long seatId;
    private Long concertId;
    private Integer seatNumber;
    private String userId;
    private Integer price;
    private LocalDateTime expiresAt;
    private Long remainingTimeSeconds;

    public static ReservationResponseDto from(ReservationResult result) {
        ReservationResponseDto dto = new ReservationResponseDto();
        dto.reservationId = result.getReservationId();
        dto.seatId = result.getSeatId();
        dto.concertId = result.getConcertId();
        dto.seatNumber = result.getSeatNumber();
        dto.userId = result.getUserId();
        dto.price = result.getPrice();
        dto.expiresAt = result.getExpiresAt();
        dto.remainingTimeSeconds = result.getRemainingTimeSeconds();
        return dto;
    }

    // Getter 메소드들

}