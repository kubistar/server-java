package kr.hhplus.be.server.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReserveSeatCommand {
    private final String userId;
    private final Long concertId;
    private final Integer seatNumber;
}