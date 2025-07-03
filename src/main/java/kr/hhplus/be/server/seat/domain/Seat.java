package kr.hhplus.be.server.seat.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "seats",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_seat_temporary_assignment",
                        columnNames = {"concert_id", "seat_number", "status", "assigned_user_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long seatId;

    @Column(name = "concert_id", nullable = false)
    private Long concertId;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SeatStatus status;

    @Column(name = "assigned_user_id")
    private String assignedUserId;

    @Column(name = "assigned_until")
    private LocalDateTime assignedUntil;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    public enum SeatStatus {
        AVAILABLE, TEMPORARILY_ASSIGNED, RESERVED
    }

    // 생성자
    public Seat(Long concertId, Integer seatNumber, BigDecimal price) {
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

    public void releaseReservation() {
        if (!isReserved()) {
            throw new IllegalStateException("예약 상태가 아닙니다.");
        }
        this.status = SeatStatus.AVAILABLE;
        this.assignedUserId = null;
        this.assignedUntil = null;
        this.reservedAt = null;
    }
}