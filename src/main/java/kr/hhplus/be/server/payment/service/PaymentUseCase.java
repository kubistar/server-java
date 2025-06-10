package kr.hhplus.be.server.payment.service;

import kr.hhplus.be.server.payment.command.PaymentCommand;
import kr.hhplus.be.server.payment.dto.PaymentResult;

public interface PaymentUseCase {
    PaymentResult processPayment(PaymentCommand command);
    PaymentResult getPaymentInfo(String paymentId);
}