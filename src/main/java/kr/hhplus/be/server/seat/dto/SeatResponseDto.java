package kr.hhplus.be.server.seat.dto;

import java.time.LocalDateTime;

/**
 * 좌석 정보 응답 DTO
 */
public class SeatResponseDto {
    private Long seatId;
    private Integer seatNumber;
    private String status;
    private Integer price;
    private LocalDateTime assignedUntil;
    private LocalDateTime reservedAt;

    protected SeatResponseDto() {}

    public static SeatResponseDto create(Long seatId, Integer seatNumber, String status,
                                         Integer price, LocalDateTime assignedUntil, LocalDateTime reservedAt) {
        SeatResponseDto dto = new SeatResponseDto();
        dto.seatId = seatId;
        dto.seatNumber = seatNumber;
        dto.status = status;
        dto.price = price;
        dto.assignedUntil = assignedUntil;
        dto.reservedAt = reservedAt;
        return dto;
    }

    // Getters
    public Long getSeatId() { return seatId; }
    public Integer getSeatNumber() { return seatNumber; }
    public String getStatus() { return status; }
    public Integer getPrice() { return price; }
    public LocalDateTime getAssignedUntil() { return assignedUntil; }
    public LocalDateTime getReservedAt() { return reservedAt; }
}