package kr.hhplus.be.server.point.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BalanceChargeCommand {
    private final String userId;
    private final Long amount;
}