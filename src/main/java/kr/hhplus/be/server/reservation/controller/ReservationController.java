package kr.hhplus.be.server.reservation.controller;

import kr.hhplus.be.server.reservation.command.ReserveSeatCommand;
import kr.hhplus.be.server.reservation.dto.ReservationRequestDto;
import kr.hhplus.be.server.reservation.dto.ReservationResult;
import kr.hhplus.be.server.reservation.service.ReserveSeatUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReserveSeatUseCase reserveSeatUseCase;

    public ReservationController(ReserveSeatUseCase reserveSeatUseCase) {
        this.reserveSeatUseCase = reserveSeatUseCase;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> reserveSeat(
            @RequestHeader("Authorization") String token,
            @RequestBody ReservationRequestDto request) {

        ReserveSeatCommand command = new ReserveSeatCommand(
                request.getUserId(),
                request.getConcertId(),
                request.getSeatNumber()
        );

        ReservationResult result = reserveSeatUseCase.reserveSeat(command);

        // 🔥 테스트에 맞는 응답 구조로 수정
        Map<String, Object> response = Map.of(
                "code", 201,
                "data", Map.of(
                        "userId", result.getUserId(),
                        "concertId", result.getConcertId(),
                        "seatNumber", result.getSeatNumber(),
                        "reservationId", result.getReservationId(),
                        "price", result.getPrice(),
                        "remainingTimeSeconds", result.getRemainingTimeSeconds()
                ),
                "message", "좌석이 임시 배정되었습니다. 5분 내에 결제를 완료해주세요."
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<Map<String, Object>> getReservationStatus(
            @RequestHeader("Authorization") String token,
            @PathVariable String reservationId) {

        ReservationResult result = reserveSeatUseCase.getReservationStatus(reservationId);

        // 🔥 테스트에 맞는 응답 구조로 수정
        Map<String, Object> response = Map.of(
                "code", 200,
                "data", Map.of(
                        "reservationId", result.getReservationId(),
                        "userId", result.getUserId(),
                        "concertId", result.getConcertId(),
                        "seatNumber", result.getSeatNumber(),
                        "price", result.getPrice(),
                        "remainingTimeSeconds", result.getRemainingTimeSeconds()
                ),
                "message", "예약 상태 조회 성공"
        );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{reservationId}")
    public ResponseEntity<Map<String, Object>> cancelReservation(
            @RequestHeader("Authorization") String token,
            @PathVariable String reservationId,
            @RequestParam String userId) {

        reserveSeatUseCase.cancelReservation(reservationId, userId);

        // 🔥 테스트에 맞는 응답 구조로 수정
        Map<String, Object> response = Map.of(
                "code", 200,
                "message", "예약이 취소되었습니다."
        );

        return ResponseEntity.ok(response);
    }
}