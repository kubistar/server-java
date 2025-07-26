package kr.hhplus.be.server.test;

import kr.hhplus.be.server.balance.command.BalanceChargeCommand;
import kr.hhplus.be.server.balance.service.BalanceServiceWithEvents;
import kr.hhplus.be.server.payment.command.PaymentCommand;
import kr.hhplus.be.server.payment.service.PaymentService;
import kr.hhplus.be.server.concert.service.ConcertServiceWithEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test/events")
@RequiredArgsConstructor
@Slf4j
public class EventTestController {

    private final BalanceServiceWithEvents balanceService;
    private final PaymentService paymentService;
    private final ConcertServiceWithEvents concertService;

    /**
     * 잔액 충전 테스트 (이벤트 발행 확인)
     */
    @PostMapping("/balance/charge")
    public String testBalanceCharge(@RequestParam String userId, @RequestParam Long amount) {
        try {
            BalanceChargeCommand command = new BalanceChargeCommand(userId, amount);
            balanceService.chargeBalance(command);
            return "잔액 충전 및 이벤트 발행 완료";
        } catch (Exception e) {
            return "실패: " + e.getMessage();
        }
    }

    /**
     * 결제 테스트 (이벤트 발행 확인)
     */
    @PostMapping("/payment/process")
    public String testPayment(@RequestParam String reservationId, @RequestParam String userId) {
        try {
            PaymentCommand command = new PaymentCommand(reservationId, userId);
            paymentService.processPayment(command);
            return "결제 처리 및 이벤트 발행 완료";
        } catch (Exception e) {
            return "실패: " + e.getMessage();
        }
    }

    /**
     * 콘서트 매진 테스트 (이벤트 발행 확인)
     */
    @PostMapping("/concert/{concertId}/soldout")
    public String testConcertSoldOut(@PathVariable Long concertId) {
        try {
            concertService.markConcertAsSoldOut(concertId);
            return "콘서트 매진 처리 및 이벤트 발행 완료";
        } catch (Exception e) {
            return "실패: " + e.getMessage();
        }
    }
}