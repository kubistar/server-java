package kr.hhplus.be.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.command.ReserveSeatCommand;
import kr.hhplus.be.server.controller.ReservationController;
import kr.hhplus.be.server.dto.ReservationResult;
import kr.hhplus.be.server.service.QueueService;
import kr.hhplus.be.server.service.ReserveSeatUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReserveSeatUseCase reserveSeatUseCase;

    @MockitoBean
    private QueueService queueService;

    @Test
    @DisplayName("POST /api/reservations - 정상적인 좌석 예약 요청")
    void whenReserveSeat_ThenShouldReturn201() throws Exception {
        // given
        ReservationResult mockResult = new ReservationResult(
                new kr.hhplus.be.server.domain.Reservation("user-123", 1L, 1L, 50000, LocalDateTime.now().plusMinutes(5)),
                15
        );

        given(reserveSeatUseCase.reserveSeat(any(ReserveSeatCommand.class))).willReturn(mockResult);

        String requestBody = """
            {
                "userId": "user-123",
                "concertId": 1,
                "seatNumber": 15
            }
            """;

        // when & then
        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.userId").value("user-123"))
                .andExpect(jsonPath("$.data.concertId").value(1))
                .andExpect(jsonPath("$.data.seatNumber").value(15))
                .andExpect(jsonPath("$.message").value("좌석이 임시 배정되었습니다. 5분 내에 결제를 완료해주세요."));
    }

    @Test
    @DisplayName("GET /api/reservations/{reservationId} - 예약 상태 조회")
    void whenGetReservationStatus_ThenShouldReturn200() throws Exception {
        // given
        String reservationId = "reservation-123";
        ReservationResult mockResult = new ReservationResult(
                new kr.hhplus.be.server.domain.Reservation("user-123", 1L, 1L, 50000, LocalDateTime.now().plusMinutes(5)),
                15
        );

        given(reserveSeatUseCase.getReservationStatus(reservationId)).willReturn(mockResult);

        // when & then
        mockMvc.perform(get("/api/reservations/{reservationId}", reservationId)
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reservationId").value(reservationId))
                .andExpect(jsonPath("$.message").value("예약 상태 조회 성공"));
    }

    @Test
    @DisplayName("DELETE /api/reservations/{reservationId} - 예약 취소")
    void whenCancelReservation_ThenShouldReturn200() throws Exception {
        // given
        String reservationId = "reservation-123";
        String userId = "user-123";

        willDoNothing().given(reserveSeatUseCase).cancelReservation(reservationId, userId);

        // when & then
        mockMvc.perform(delete("/api/reservations/{reservationId}", reservationId)
                .header("Authorization", "Bearer token-123")
                .param("userId", userId))
                .andExpect( status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("예약이 취소되었습니다."));
    }
}
