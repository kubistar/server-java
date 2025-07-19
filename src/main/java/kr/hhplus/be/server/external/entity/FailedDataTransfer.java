package kr.hhplus.be.server.external.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "failed_data_transfer")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedDataTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String reservationId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Long concertId;

    @Column(nullable = false)
    private Integer seatNumber;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private LocalDateTime reservedAt;

    @Column(nullable = false)
    private LocalDateTime failedAt;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private Integer retryCount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FailedDataStatus status;

    private LocalDateTime lastRetryAt;

    // 재시도 횟수 증가
    public void incrementRetryCount() {
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();
    }

    // 상태 변경
    public void updateStatus(FailedDataStatus status) {
        this.status = status;
    }
}