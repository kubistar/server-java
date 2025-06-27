package kr.hhplus.be.server.reservation.dto;

import kr.hhplus.be.server.reservation.domain.Reservation;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class ReservationResult {
    private final String reservationId;
    private final Long seatId;
    private final Long concertId;
    private final Integer seatNumber;
    private final String userId;
    private final BigDecimal price; // Integer → BigDecimal 변경
    private final LocalDateTime expiresAt;
    private final long remainingTimeSeconds;

    public ReservationResult(Reservation reservation, Integer seatNumber) {
        this.reservationId = reservation.getReservationId();
        this.seatId = reservation.getSeatId();
        this.concertId = reservation.getConcertId();
        this.seatNumber = seatNumber;
        this.userId = reservation.getUserId();
        this.price = reservation.getPrice(); // BigDecimal 그대로 사용
        this.expiresAt = reservation.getExpiresAt();
        this.remainingTimeSeconds = reservation.getRemainingTimeSeconds();
    }

    // 호환성을 위한 Integer 반환 메서드 (필요시)
    public Integer getPriceAsInteger() {
        return price != null ? price.intValue() : 0;
    }

    // Long 반환 메서드 (PaymentResult와 일관성)
    public Long getPriceAsLong() {
        return price != null ? price.longValue() : 0L;
    }

    // 문자열 형태로 포맷된 가격 반환 (화면 표시용)
    public String getFormattedPrice() {
        return price != null ? String.format("%,d원", price.longValue()) : "0원";
    }
}