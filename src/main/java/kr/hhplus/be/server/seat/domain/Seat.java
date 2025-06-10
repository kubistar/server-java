package kr.hhplus.be.server.seat.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seatId;
    private Long concertId;
    private Integer seatNumber;
    private Integer price;
    private SeatStatus status;
    private String assignedUserId;
    private LocalDateTime assignedUntil;
    private LocalDateTime reservedAt;

    public enum SeatStatus {
        AVAILABLE, TEMPORARILY_ASSIGNED, RESERVED
    }

    protected Seat() {
        // JPA에서 사용할 기본 생성자
    }

    // 생성자
    public Seat(Long seatId, Long concertId, Integer seatNumber, Integer price) {
        this.seatId = seatId;
        this.concertId = concertId;
        this.seatNumber = seatNumber;
        this.price = price;
        this.status = SeatStatus.AVAILABLE;
    }

    // 비즈니스 로직 메소드
    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE;
    }

    public boolean isTemporarilyAssigned() {
        return status == SeatStatus.TEMPORARILY_ASSIGNED;
    }

    public boolean isReserved() {
        return status == SeatStatus.RESERVED;
    }

    public boolean isExpired() {
        if (!isTemporarilyAssigned()) {
            return false;
        }
        return assignedUntil != null && LocalDateTime.now().isAfter(assignedUntil);
    }

    public void assignTemporarily(String userId, LocalDateTime expiresAt) {
        if (!isAvailable()) {
            throw new IllegalStateException("좌석이 이미 배정되었습니다.");
        }
        this.status = SeatStatus.TEMPORARILY_ASSIGNED;
        this.assignedUserId = userId;
        this.assignedUntil = expiresAt;
    }

    public void confirmReservation(LocalDateTime confirmedAt) {
        if (!isTemporarilyAssigned()) {
            throw new IllegalStateException("임시 배정 상태가 아닙니다.");
        }
        if (isExpired()) {
            throw new IllegalStateException("임시 배정 시간이 만료되었습니다.");
        }
        this.status = SeatStatus.RESERVED;
        this.reservedAt = confirmedAt;
    }

    public void releaseAssignment() {
        if (!isTemporarilyAssigned()) {
            throw new IllegalStateException("임시 배정 상태가 아닙니다.");
        }
        this.status = SeatStatus.AVAILABLE;
        this.assignedUserId = null;
        this.assignedUntil = null;
    }


}