package kr.hhplus.be.server.payment.controller;

import kr.hhplus.be.server.payment.command.PaymentCommand;
import kr.hhplus.be.server.payment.dto.PaymentRequestDto;
import kr.hhplus.be.server.payment.dto.PaymentResult;
import kr.hhplus.be.server.payment.service.PaymentUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentUseCase paymentUseCase;

    @PostMapping
    public ResponseEntity<PaymentResult> processPayment(@RequestBody PaymentRequestDto request) {
        PaymentCommand command = new PaymentCommand(request.getReservationId(), request.getUserId());
        PaymentResult result = paymentUseCase.processPayment(command);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResult> getPaymentInfo(@PathVariable String paymentId) {
        PaymentResult result = paymentUseCase.getPaymentInfo(paymentId);
        return ResponseEntity.ok(result);
    }
}
