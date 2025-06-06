package kr.hhplus.be.server.service;

import kr.hhplus.be.server.command.PaymentCommand;
import kr.hhplus.be.server.dto.PaymentResult;

public interface PaymentUseCase {
    PaymentResult processPayment(PaymentCommand command);
    PaymentResult getPaymentInfo(String paymentId);
}