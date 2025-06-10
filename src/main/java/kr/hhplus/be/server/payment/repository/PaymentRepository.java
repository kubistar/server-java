package kr.hhplus.be.server.payment.repository;

import kr.hhplus.be.server.payment.domain.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(String paymentId);
    List<Payment> findByUserId(String userId);
    Optional<Payment> findByReservationId(String reservationId);
}