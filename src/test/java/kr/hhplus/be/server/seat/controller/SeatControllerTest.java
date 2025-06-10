package kr.hhplus.be.server.seat.controller;

import kr.hhplus.be.server.common.GlobalExceptionHandler;
import kr.hhplus.be.server.seat.dto.SeatPageResponse;
import kr.hhplus.be.server.seat.dto.SeatResponseDto;
import kr.hhplus.be.server.concert.exception.ConcertNotFoundException;
import kr.hhplus.be.server.queue.service.QueueService;
import kr.hhplus.be.server.seat.service.SeatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@ExtendWith(MockitoExtension.class)
class SeatControllerTest {

    private static final Logger log = LoggerFactory.getLogger(SeatControllerTest.class);

    private MockMvc mockMvc;

    @Mock
    private SeatService seatService;

    @Mock
    private QueueService queueService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        log.info("=== 테스트 준비: SeatController MockMvc 설정 ===");

        SeatController seatController = new SeatController(seatService, queueService);
        mockMvc = MockMvcBuilders.standaloneSetup(seatController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        log.info("MockMvc 설정 완료");
    }

    @Test
    @DisplayName("유효한 토큰으로 좌석 조회 API 호출 시 성공한다")
    void getConcertSeats_WithValidToken_ShouldReturnSeats() throws Exception {
        // given
        log.info("=== 테스트 시작: 유효한 토큰으로 좌석 조회 ===");

        Long concertId = 1L;
        String validToken = "valid-queue-token-123";

        // QueueService Mock 설정 추가
        when(queueService.validateActiveToken(validToken)).thenReturn(true);

        SeatPageResponse mockResponse = createMockSeatPageResponse(concertId);
        log.info("Mock 응답 데이터 생성 완료: concertId={}, totalSeats={}",
                concertId, mockResponse.getSummary().getTotalSeats());

        when(seatService.getConcertSeats(concertId))
                .thenReturn(mockResponse);
        log.info("Mock SeatService 설정 완료");

        // when & then
        log.info("API 호출 시작: GET /api/concerts/{}/seats with token={}", concertId, validToken);

        mockMvc.perform(get("/api/concerts/{concertId}/seats", concertId)
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());
        // ... 나머지 검증 로직 동일

        verify(queueService).validateActiveToken(validToken); // 추가 검증
        verify(seatService).getConcertSeats(concertId);
        log.info("✓ QueueService 및 SeatService 호출 검증 완료");

        log.info("=== 테스트 완료: 좌석 조회 API 성공 검증 통과 ===");
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 좌석 조회 시 403 에러를 반환한다")
    void getConcertSeats_WithInvalidToken_ShouldReturn403() throws Exception {
        // given
        Long concertId = 1L;
        String invalidToken = "invalid-token-123";

        // QueueService Mock 설정: 유효하지 않은 토큰
        when(queueService.validateActiveToken(invalidToken)).thenReturn(false);

        // when & then
        mockMvc.perform(get("/api/concerts/{concertId}/seats", concertId)
                        .header("Authorization", "Bearer " + invalidToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden());

        verify(queueService).validateActiveToken(invalidToken);
        verify(seatService, never()).getConcertSeats(any()); // SeatService는 호출되지 않아야 함
    }

    @Test
    @DisplayName("Authorization 헤더 없이 좌석 조회 시 401 에러를 반환한다")
    void getConcertSeats_WithoutAuthHeader_ShouldReturn401() throws Exception {
        // given
        log.info("=== 테스트 시작: Authorization 헤더 없이 좌석 조회 ===");

        Long concertId = 1L;
        log.info("테스트 콘서트 ID: {}", concertId);

        // when & then
        log.info("API 호출 시작: GET /api/concerts/{}/seats (Authorization 헤더 없음)", concertId);

        mockMvc.perform(get("/api/concerts/{concertId}/seats", concertId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest()); // Authorization 헤더가 없으면 400 에러

        // SeatService는 호출되지 않아야 함
        verify(seatService, never()).getConcertSeats(any());
        log.info("✓ SeatService 미호출 검증 완료");

        log.info("=== 테스트 완료: Authorization 헤더 없음 에러 검증 통과 ===");
    }

    @Test
    @DisplayName("잘못된 형식의 Authorization 헤더로 좌석 조회 시 400 에러를 반환한다")
    void getConcertSeats_WithInvalidAuthHeader_ShouldReturn400() throws Exception {
        // given
        log.info("=== 테스트 시작: 잘못된 Authorization 헤더로 좌석 조회 ===");

        Long concertId = 1L;
        String invalidToken = "invalid-format-token"; // Bearer 없음
        log.info("잘못된 토큰 형식: {}", invalidToken);

        // when & then
        log.info("API 호출 시작: GET /api/concerts/{}/seats with invalid token format", concertId);

        mockMvc.perform(get("/api/concerts/{concertId}/seats", concertId)
                        .header("Authorization", invalidToken) // Bearer 없음
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());

        // SeatService는 호출되지 않아야 함
        verify(seatService, never()).getConcertSeats(any());
        log.info("✓ SeatService 미호출 검증 완료");

        log.info("=== 테스트 완료: 잘못된 Authorization 헤더 에러 검증 통과 ===");
    }

    @Test
    @DisplayName("존재하지 않는 콘서트 좌석 조회 시 404 에러를 반환한다")
    void getConcertSeats_NonExistingConcert_ShouldReturn404() throws Exception {
        // given
        log.info("=== 테스트 시작: 존재하지 않는 콘서트 좌석 조회 ===");

        Long nonExistingConcertId = 999L;
        String validToken = "valid-queue-token-123";
        log.info("존재하지 않는 콘서트 ID: {}", nonExistingConcertId);

        // QueueService Mock 설정 추가 - 중요!
        when(queueService.validateActiveToken(validToken)).thenReturn(true);

        when(seatService.getConcertSeats(nonExistingConcertId))
                .thenThrow(new ConcertNotFoundException("콘서트를 찾을 수 없습니다: " + nonExistingConcertId));
        log.info("Mock SeatService 예외 설정 완료");

        // when & then
        log.info("API 호출 시작: GET /api/concerts/{}/seats (존재하지 않는 콘서트)", nonExistingConcertId);

        mockMvc.perform(get("/api/concerts/{concertId}/seats", nonExistingConcertId)
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.error.type").value("CONCERT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("콘서트를 찾을 수 없습니다: " + nonExistingConcertId));

        verify(queueService).validateActiveToken(validToken); // 검증 추가
        verify(seatService).getConcertSeats(nonExistingConcertId);
        log.info("✓ QueueService 및 SeatService 호출 검증 완료");

        log.info("=== 테스트 완료: 존재하지 않는 콘서트 404 에러 검증 통과 ===");
    }

    @Test
    @DisplayName("정상적인 토큰으로 다양한 콘서트 좌석을 조회할 수 있다")
    void getConcertSeats_DifferentConcerts_ShouldReturnDifferentSeats() throws Exception {
        // given
        log.info("=== 테스트 시작: 다양한 콘서트 좌석 조회 ===");

        Long concertId1 = 1L;
        Long concertId2 = 2L;
        String validToken = "valid-queue-token-123";

        // QueueService Mock 설정 추가 - 중요!
        when(queueService.validateActiveToken(validToken)).thenReturn(true);

        SeatPageResponse response1 = createMockSeatPageResponse(concertId1, "IU Concert", 50);
        SeatPageResponse response2 = createMockSeatPageResponse(concertId2, "BTS Concert", 30);

        log.info("Mock 응답 데이터 생성:");
        log.info("  - Concert1: ID={}, Title={}, Seats={}", concertId1, "IU Concert", 50);
        log.info("  - Concert2: ID={}, Title={}, Seats={}", concertId2, "BTS Concert", 30);

        when(seatService.getConcertSeats(concertId1)).thenReturn(response1);
        when(seatService.getConcertSeats(concertId2)).thenReturn(response2);
        log.info("Mock SeatService 설정 완료");

        // when & then - 첫 번째 콘서트
        log.info("첫 번째 콘서트 API 호출: GET /api/concerts/{}/seats", concertId1);

        mockMvc.perform(get("/api/concerts/{concertId}/seats", concertId1)
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.concertId").value(concertId1))
                .andExpect(jsonPath("$.data.concertTitle").value("IU Concert"))
                .andExpect(jsonPath("$.data.seats.length()").value(50))
                .andExpect(jsonPath("$.data.summary.totalSeats").value(50));

        log.info("✓ 첫 번째 콘서트 좌석 조회 검증 완료");

        // when & then - 두 번째 콘서트
        log.info("두 번째 콘서트 API 호출: GET /api/concerts/{}/seats", concertId2);

        mockMvc.perform(get("/api/concerts/{concertId}/seats", concertId2)
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.concertId").value(concertId2))
                .andExpect(jsonPath("$.data.concertTitle").value("BTS Concert"))
                .andExpect(jsonPath("$.data.seats.length()").value(30))
                .andExpect(jsonPath("$.data.summary.totalSeats").value(30));

        log.info("✓ 두 번째 콘서트 좌석 조회 검증 완료");

        // 검증 추가
        verify(queueService, times(2)).validateActiveToken(validToken); // 2번 호출됨
        verify(seatService).getConcertSeats(concertId1);
        verify(seatService).getConcertSeats(concertId2);
        log.info("✓ QueueService 및 SeatService 호출 검증 완료");

        log.info("=== 테스트 완료: 다양한 콘서트 좌석 조회 검증 통과 ===");
    }

    /**
     * Mock SeatPageResponse 생성 (기본 설정)
     */
    private SeatPageResponse createMockSeatPageResponse(Long concertId) {
        return createMockSeatPageResponse(concertId, "Test Concert", 50);
    }

    /**
     * Mock SeatPageResponse 생성 (커스텀 설정)
     */
    private SeatPageResponse createMockSeatPageResponse(Long concertId, String title, int totalSeats) {
        log.debug("Mock SeatPageResponse 생성: concertId={}, title={}, totalSeats={}",
                concertId, title, totalSeats);

        // 좌석 목록 생성
        List<SeatResponseDto> seats = new ArrayList<>();
        for (int i = 1; i <= totalSeats; i++) {
            SeatResponseDto seat = SeatResponseDto.create(
                    (long) i,           // seatId
                    i,                  // seatNumber
                    "AVAILABLE",        // status
                    50000,              // price
                    null,               // assignedUntil
                    null                // reservedAt
            );
            seats.add(seat);
        }
        log.debug("좌석 목록 생성 완료: {}개", seats.size());

        // 좌석 요약 정보 생성
        SeatPageResponse.SeatSummary summary = new SeatPageResponse.SeatSummary(
                totalSeats,     // totalSeats
                totalSeats,     // availableSeats
                0,              // temporarilyAssignedSeats
                0               // reservedSeats
        );
        log.debug("좌석 요약 정보 생성 완료");

        // SeatPageResponse 생성
        SeatPageResponse response = SeatPageResponse.builder()
                .concertId(concertId)
                .concertTitle(title)
                .concertDate(LocalDate.of(2025, 6, 1))
                .concertTime(LocalTime.of(19, 0))
                .venue("Test Venue")
                .seats(seats)
                .summary(summary)
                .build();

        log.debug("SeatPageResponse 생성 완료");
        return response;
    }
}
