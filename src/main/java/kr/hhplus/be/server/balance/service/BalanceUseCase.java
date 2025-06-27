package kr.hhplus.be.server.balance.service;

import kr.hhplus.be.server.balance.command.BalanceChargeCommand;
import kr.hhplus.be.server.balance.dto.BalanceResult;

public interface BalanceUseCase {
    BalanceResult chargeBalance(BalanceChargeCommand command);
    BalanceResult getBalance(String userId);
}
