package kr.hhplus.be.server.reservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.reservation.command.ReserveSeatCommand;
import kr.hhplus.be.server.reservation.dto.ReservationResult;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.queue.service.QueueService;
import kr.hhplus.be.server.reservation.service.ReserveSeatUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReserveSeatUseCase reserveSeatUseCase;

    @MockitoBean
    private QueueService queueService;

    @BeforeEach
    void setUp() {
        // 모든 테스트에서 토큰 검증이 성공하도록 설정
        given(queueService.validateActiveToken(anyString())).willReturn(true);
    }

    @Test
    @DisplayName("POST /api/reservations - 정상적인 좌석 예약 요청")
    void whenReserveSeat_ThenShouldReturn201() throws Exception {
        // given
        ReservationResult mockResult = new ReservationResult(
                new Reservation("user-123", 1L, 1L, BigDecimal.valueOf(50000), LocalDateTime.now().plusMinutes(5)),
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
                new Reservation("user-123", 1L, 1L, BigDecimal.valueOf(50000), LocalDateTime.now().plusMinutes(5)),
                15
        );

        given(reserveSeatUseCase.getReservationStatus(reservationId)).willReturn(mockResult);

        // when & then
        mockMvc.perform(get("/api/reservations/{reservationId}", reservationId)
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("예약 상태 조회 성공"))
                .andExpect(jsonPath("$.data.reservationId").isString())
                .andExpect(jsonPath("$.data.userId").value("user-123"))
                .andExpect(jsonPath("$.data.concertId").value(1))
                .andExpect(jsonPath("$.data.seatNumber").value(15))
                .andExpect(jsonPath("$.data.price").value(50000));
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