package kr.hhplus.be.server.seat;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.queue.domain.QueueStatus;
import kr.hhplus.be.server.queue.domain.QueueToken;
import kr.hhplus.be.server.concert.repository.ConcertRepository;
import kr.hhplus.be.server.queue.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;


import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SeatControllerIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(SeatControllerIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConcertRepository concertRepository;

    @MockitoBean
    private QueueService queueService; // 대기열 서비스는 Mock 처리

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        log.info("=== 통합 테스트 준비 ===");

        // 테스트 콘서트 데이터 준비
        Concert concert1 = new Concert(
                "IU 2025 Concert",
                "IU",
                "올림픽공원 체조경기장",
                LocalDate.of(2025, 6, 1),
                LocalTime.of(19, 0),
                50
        );

        Concert concert2 = new Concert(
                "BTS World Tour",
                "BTS",
                "잠실종합운동장",
                LocalDate.of(2025, 7, 15),
                LocalTime.of(18, 30),
                30
        );

        concertRepository.save(concert1);
        concertRepository.save(concert2);

        log.info("테스트 콘서트 데이터 저장 완료");
    }

    @Test
    @DisplayName("실제 데이터베이스를 사용한 좌석 조회 통합 테스트")
    void getConcertSeats_IntegrationTest_ShouldReturnRealData() throws Exception {
        // given
        log.info("=== 통합 테스트 시작: 실제 DB를 사용한 좌석 조회 ===");

        String validToken = "valid-integration-test-token";

        // 유효한 활성 토큰으로 Mock 설정
        QueueToken mockQueueToken = new QueueToken(
                validToken,
                "test-user",
                0L,
                0,
                QueueStatus.ACTIVE,
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(30)
        );

        when(queueService.validateActiveToken(validToken)).thenReturn(true);
        when(queueService.getQueueStatus(validToken)).thenReturn(mockQueueToken);
        log.info("Mock 대기열 서비스 설정 완료: 유효한 활성 토큰");

        // 저장된 콘서트 조회
        Concert savedConcert = concertRepository.findAll().get(0);
        Long concertId = savedConcert.getConcertId();
        log.info("저장된 콘서트 정보: ID={}, Title={}, TotalSeats={}",
                concertId, savedConcert.getTitle(), savedConcert.getTotalSeats());

        // when & then
        log.info("통합 테스트 API 호출: GET /api/concerts/{}/seats", concertId);

        mockMvc.perform(get("/api/concerts/{concertId}/seats", concertId)
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("좌석 정보 조회 성공"))
                .andExpect(jsonPath("$.data.concertId").value(concertId))
                .andExpect(jsonPath("$.data.concertTitle").value(savedConcert.getTitle()))
                .andExpect(jsonPath("$.data.venue").value(savedConcert.getVenue()))
                .andExpect(jsonPath("$.data.seats").isArray())
                .andExpect(jsonPath("$.data.seats.length()").value(savedConcert.getTotalSeats()))
                .andExpect(jsonPath("$.data.summary.totalSeats").value(savedConcert.getTotalSeats()))
                .andExpect(jsonPath("$.data.summary.availableSeats").value(savedConcert.getTotalSeats()));

        log.info("✓ 통합 테스트 검증 완료");
        log.info("=== 통합 테스트 완료: 실제 DB 좌석 조회 성공 ===");
    }

    @Test
    @DisplayName("대기열 토큰 검증 실패 시 403 에러 반환 통합 테스트")
    void getConcertSeats_InvalidToken_IntegrationTest() throws Exception {
        // given
        log.info("=== 통합 테스트 시작: 유효하지 않은 토큰으로 좌석 조회 ===");

        String invalidToken = "invalid-token-123";

        // 유효하지 않은 토큰으로 Mock 설정
        when(queueService.validateActiveToken(invalidToken)).thenReturn(false);
        log.info("Mock 대기열 서비스 설정 완료: 유효하지 않은 토큰");

        Concert savedConcert = concertRepository.findAll().get(0);
        Long concertId = savedConcert.getConcertId();

        // when & then
        log.info("통합 테스트 API 호출: GET /api/concerts/{}/seats (유효하지 않은 토큰)", concertId);

        mockMvc.perform(get("/api/concerts/{concertId}/seats", concertId)
                        .header("Authorization", "Bearer " + invalidToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden());

        log.info("✓ 유효하지 않은 토큰 403 에러 검증 완료");
        log.info("=== 통합 테스트 완료: 토큰 검증 실패 처리 성공 ===");
    }

    @Test
    @DisplayName("존재하지 않는 콘서트 조회 통합 테스트")
    void getConcertSeats_NonExistingConcert_IntegrationTest() throws Exception {
        // given
        log.info("=== 통합 테스트 시작: 존재하지 않는 콘서트 조회 ===");

        String validToken = "valid-token-123";
        Long nonExistingConcertId = 999L;

        when(queueService.validateActiveToken(validToken)).thenReturn(true);
        log.info("Mock 대기열 서비스 설정 완료");

        // when & then
        log.info("통합 테스트 API 호출: GET /api/concerts/{}/seats (존재하지 않는 콘서트)",
                nonExistingConcertId);

        mockMvc.perform(get("/api/concerts/{concertId}/seats", nonExistingConcertId)
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.error.type").value("CONCERT_NOT_FOUND"));

        log.info("✓ 존재하지 않는 콘서트 404 에러 검증 완료");
        log.info("=== 통합 테스트 완료: 존재하지 않는 콘서트 처리 성공 ===");
    }
}