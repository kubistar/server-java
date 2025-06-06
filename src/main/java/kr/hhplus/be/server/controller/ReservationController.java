package kr.hhplus.be.server.controller;

import kr.hhplus.be.server.command.ReserveSeatCommand;
import kr.hhplus.be.server.common.ApiResponse;
import kr.hhplus.be.server.dto.ReservationRequestDto;
import kr.hhplus.be.server.dto.ReservationResponseDto;
import kr.hhplus.be.server.dto.ReservationResult;
import kr.hhplus.be.server.service.ReserveSeatUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReserveSeatUseCase reserveSeatUseCase;

    public ReservationController(ReserveSeatUseCase reserveSeatUseCase) {
        this.reserveSeatUseCase = reserveSeatUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponseDto>> reserveSeat(
            @RequestHeader("Authorization") String token,
            @RequestBody ReservationRequestDto request) {

        ReserveSeatCommand command = new ReserveSeatCommand(
                request.getUserId(),
                request.getConcertId(),
                request.getSeatNumber()
        );

        ReservationResult result = reserveSeatUseCase.reserveSeat(command);

        ReservationResponseDto response = ReservationResponseDto.from(result);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "좌석이 임시 배정되었습니다. 5분 내에 결제를 완료해주세요."));
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponseDto>> getReservationStatus(
            @RequestHeader("Authorization") String token,
            @PathVariable String reservationId) {

        ReservationResult result = reserveSeatUseCase.getReservationStatus(reservationId);
        ReservationResponseDto response = ReservationResponseDto.from(result);

        return ResponseEntity.ok(ApiResponse.success(response, "예약 상태 조회 성공"));
    }

    @DeleteMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<Void>> cancelReservation(
            @RequestHeader("Authorization") String token,
            @PathVariable String reservationId,
            @RequestParam String userId) {

        reserveSeatUseCase.cancelReservation(reservationId, userId);

        return ResponseEntity.ok(ApiResponse.success(null, "예약이 취소되었습니다."));
    }
}

