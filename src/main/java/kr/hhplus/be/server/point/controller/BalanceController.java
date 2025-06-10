package kr.hhplus.be.server.point.controller;

import kr.hhplus.be.server.point.command.BalanceChargeCommand;
import kr.hhplus.be.server.point.dto.BalanceChargeRequestDto;
import kr.hhplus.be.server.point.dto.BalanceResult;
import kr.hhplus.be.server.point.service.BalanceUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceUseCase balanceUseCase;

    @PostMapping("/{userId}/balance")
    public ResponseEntity<BalanceResult> chargeBalance(
            @PathVariable String userId,
            @RequestBody BalanceChargeRequestDto request) {

        BalanceChargeCommand command = new BalanceChargeCommand(userId, request.getAmount());
        BalanceResult result = balanceUseCase.chargeBalance(command);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{userId}/balance")
    public ResponseEntity<BalanceResult> getBalance(@PathVariable String userId) {
        BalanceResult result = balanceUseCase.getBalance(userId);
        return ResponseEntity.ok(result);
    }
}
