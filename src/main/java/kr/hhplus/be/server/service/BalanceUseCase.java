package kr.hhplus.be.server.service;

import kr.hhplus.be.server.command.BalanceChargeCommand;
import kr.hhplus.be.server.dto.BalanceResult;

public interface BalanceUseCase {
    BalanceResult chargeBalance(BalanceChargeCommand command);
    BalanceResult getBalance(String userId);
}
