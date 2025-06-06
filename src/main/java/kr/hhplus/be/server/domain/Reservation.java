package kr.hhplus.be.server.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
public class Reservation {
    @Id
    private String reservationId;
    private String userId;
    private Long concertId;
    private Long seatId;
    private ReservationStatus status;
    private Integer price;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime confirmedAt;

    public enum ReservationStatus {
        TEMPORARILY_ASSIGNED, CONFIRMED, CANCELLED, EXPIRED
    }

    protected Reservation() {
        // JPA 기본 생성자
    }

    // 생성자
    public Reservation(String userId, Long concertId, Long seatId, Integer price, LocalDateTime expiresAt) {
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

    public void confirm(LocalDateTime confirmedAt) {
        if (status != ReservationStatus.TEMPORARILY_ASSIGNED) {
            throw new IllegalStateException("임시 배정 상태가 아닙니다.");
        }
        if (isExpired()) {
            throw new IllegalStateException("예약이 만료되었습니다.");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }

    public void cancel() {
        if (status != ReservationStatus.TEMPORARILY_ASSIGNED) {
            throw new IllegalStateException("취소할 수 없는 상태입니다.");
        }
        this.status = ReservationStatus.CANCELLED;
    }

    public void expire() {
        if (status != ReservationStatus.TEMPORARILY_ASSIGNED) {
            throw new IllegalStateException("만료 처리할 수 없는 상태입니다.");
        }
        this.status = ReservationStatus.EXPIRED;
    }

    public long getRemainingTimeSeconds() {
        if (isExpired()) {
            return 0;
        }
        return java.time.Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
    }
}