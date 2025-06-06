package kr.hhplus.be.server.repository;

import kr.hhplus.be.server.domain.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<Payment> findById(String paymentId) {
        return paymentJpaRepository.findById(paymentId);
    }

    @Override
    public List<Payment> findByUserId(String userId) {
        return paymentJpaRepository.findByUserId(userId);
    }

    @Override
    public Optional<Payment> findByReservationId(String reservationId) {
        return paymentJpaRepository.findByReservationId(reservationId);
    }
}