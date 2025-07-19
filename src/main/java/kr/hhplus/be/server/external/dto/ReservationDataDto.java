package kr.hhplus.be.server.external.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReservationDataDto(
        String reservationId,
        String userId,
        Long concertId,
        Integer seatNumber,
        BigDecimal price,
        LocalDateTime reservedAt
) {

}