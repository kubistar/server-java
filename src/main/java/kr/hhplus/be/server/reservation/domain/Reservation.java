package kr.hhplus.be.server.reservation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @Column(name = "reservation_id", length = 36)
    private String reservationId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "concert_id", nullable = false)
    private Long concertId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price; // Integer → BigDecimal로 변경

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    public void updateExpirationTime(LocalDateTime localDateTime) {
    }

    public enum ReservationStatus {
        TEMPORARILY_ASSIGNED, CONFIRMED, CANCELLED, EXPIRED
    }

    // 생성자
    public Reservation(String userId, Long concertId, Long seatId, BigDecimal price, LocalDateTime expiresAt) {
        validateReservationData(userId, concertId, seatId, price, expiresAt);

        this.reservationId = UUID.randomUUID().toString();
        this.userId = userId;
        this.concertId = concertId;
        this.seatId = seatId;
        this.price = price;
        this.status = ReservationStatus.TEMPORARILY_ASSIGNED;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
    }

    // 비즈니스 로직 메소드
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isTemporarilyAssigned() {
        return status == ReservationStatus.TEMPORARILY_ASSIGNED;
    }

    public boolean isConfirmed() {
        return status == ReservationStatus.CONFIRMED;
    }

    public boolean isCancelled() {
        return status == ReservationStatus.CANCELLED;
    }

    public boolean isActive() {
        return status == ReservationStatus.TEMPORARILY_ASSIGNED || status == ReservationStatus.CONFIRMED;
    }

    public void confirm(LocalDateTime confirmedAt) {
        if (status != ReservationStatus.TEMPORARILY_ASSIGNED) {
            throw new IllegalStateException("임시 배정 상태가 아닙니다. 현재 상태: " + status);
        }
        if (isExpired()) {
            throw new IllegalStateException("예약이 만료되었습니다.");
        }
        if (confirmedAt == null) {
            throw new IllegalArgumentException("확정 시간은 필수입니다.");
        }

        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }

    public void cancel() {
        if (status != ReservationStatus.TEMPORARILY_ASSIGNED) {
            throw new IllegalStateException("취소할 수 없는 상태입니다. 현재 상태: " + status);
        }
        this.status = ReservationStatus.CANCELLED;
    }

    public void expire() {
        if (status != ReservationStatus.TEMPORARILY_ASSIGNED) {
            throw new IllegalStateException("만료 처리할 수 없는 상태입니다. 현재 상태: " + status);
        }
        this.status = ReservationStatus.EXPIRED;
    }

    public long getRemainingTimeSeconds() {
        if (isExpired()) {
            return 0;
        }
        return java.time.Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
    }

    public long getRemainingTimeMinutes() {
        return getRemainingTimeSeconds() / 60;
    }

    // 가격 관련 메서드
    public boolean isPriceValid() {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }

    // 유효성 검증
    private void validateReservationData(String userId, Long concertId, Long seatId,
                                         BigDecimal price, LocalDateTime expiresAt) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (userId.length() > 50) {
            throw new IllegalArgumentException("사용자 ID는 50자를 초과할 수 없습니다.");
        }

        if (concertId == null || concertId <= 0) {
            throw new IllegalArgumentException("유효한 콘서트 ID가 필요합니다.");
        }

        if (seatId == null || seatId <= 0) {
            throw new IllegalArgumentException("유효한 좌석 ID가 필요합니다.");
        }

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("가격은 0보다 커야 합니다.");
        }

        if (expiresAt == null) {
            throw new IllegalArgumentException("만료 시간은 필수입니다.");
        }
        if (expiresAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("만료 시간은 현재 시간 이후여야 합니다.");
        }
    }
}