package kr.hhplus.be.server.reservation.event;

import kr.hhplus.be.server.common.event.BaseEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class ReservationCancelledEvent extends BaseEvent {

    private String reservationId;
    private String userId;
    private Long concertId;
    private Integer seatNumber;
    private BigDecimal refundAmount;
    private String cancellationReason;

    public ReservationCancelledEvent(String reservationId, String userId, Long concertId,
                                     Integer seatNumber, BigDecimal refundAmount, String cancellationReason) {
        super("RESERVATION_CANCELLED");
        this.reservationId = reservationId;
        this.userId = userId;
        this.concertId = concertId;
        this.seatNumber = seatNumber;
        this.refundAmount = refundAmount;
        this.cancellationReason = cancellationReason;
    }
}