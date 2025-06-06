package kr.hhplus.be.server.repository;

import kr.hhplus.be.server.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, String> {
    List<Payment> findByUserId(String userId);
    Optional<Payment> findByReservationId(String reservationId);
}
