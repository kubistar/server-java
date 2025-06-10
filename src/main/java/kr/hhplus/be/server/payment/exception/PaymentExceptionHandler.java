package kr.hhplus.be.server.payment.exception;

import kr.hhplus.be.server.exception.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class PaymentExceptionHandler {

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException e) {
        log.warn("잔액 부족: {}", e.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("currentBalance", e.getCurrentBalance());
        details.put("requiredAmount", e.getRequiredAmount());
        details.put("shortfallAmount", e.getShortfallAmount());

        ErrorResponse error = new ErrorResponse(
                "INSUFFICIENT_BALANCE",
                e.getMessage(),
                details
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException e) {
        log.warn("결제 정보 조회 실패: {}", e.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("paymentId", e.getPaymentId());

        ErrorResponse error = new ErrorResponse(
                "PAYMENT_NOT_FOUND",
                e.getMessage(),
                details
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("잘못된 요청: {}", e.getMessage());

        ErrorResponse error = new ErrorResponse(
                "INVALID_REQUEST",
                e.getMessage(),
                null
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}