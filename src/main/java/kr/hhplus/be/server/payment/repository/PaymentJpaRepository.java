package kr.hhplus.be.server.payment.repository;

import kr.hhplus.be.server.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentJpaRepository extends JpaRepository<Payment, String> {
    List<Payment> findByUserId(String userId);
    Optional<Payment> findByReservationId(String reservationId);
}
