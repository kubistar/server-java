package kr.hhplus.be.server.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentCommand {
    private final String reservationId;
    private final String userId;
}