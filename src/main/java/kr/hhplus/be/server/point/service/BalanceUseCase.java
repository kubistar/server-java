package kr.hhplus.be.server.point.service;

import kr.hhplus.be.server.point.command.BalanceChargeCommand;
import kr.hhplus.be.server.point.dto.BalanceResult;

public interface BalanceUseCase {
    BalanceResult chargeBalance(BalanceChargeCommand command);
    BalanceResult getBalance(String userId);
}
