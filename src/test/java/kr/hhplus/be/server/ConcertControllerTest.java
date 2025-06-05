package kr.hhplus.be.server;

import kr.hhplus.be.server.common.GlobalExceptionHandler;
import kr.hhplus.be.server.controller.ConcertController;
import kr.hhplus.be.server.dto.ConcertResponseDto;
import kr.hhplus.be.server.exception.ConcertNotFoundException;
import kr.hhplus.be.server.service.ConcertService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebMvcTest(ConcertController.class)
@Import(GlobalExceptionHandler.class)
class ConcertControllerTest {

    private static final Logger log = LoggerFactory.getLogger(ConcertControllerTest.class);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConcertService concertService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("예약 가능한 콘서트 목록 조회 API 테스트")
    void getAvailableConcerts_ShouldReturnPagedConcerts() throws Exception {
        // given
        log.info("=== 테스트 시작: 콘서트 목록 조회 ===");

        ConcertResponseDto concert1 = createTestConcertDto(1L, "Concert 1", "Artist 1");
        ConcertResponseDto concert2 = createTestConcertDto(2L, "Concert 2", "Artist 2");
        log.info("생성된 콘서트1: ID={}, Title={}", concert1.getConcertId(), concert1.getTitle());
        log.info("생성된 콘서트2: ID={}, Title={}", concert2.getConcertId(), concert2.getTitle());

        List<ConcertResponseDto> concerts = Arrays.asList(concert1, concert2);
        Page<ConcertResponseDto> concertPage = new PageImpl<>(concerts, PageRequest.of(0, 20), 2);
        log.info("Mock 페이지 데이터 준비 완료 - 총 {}개 콘서트", concerts.size());

        when(concertService.getAvailableConcerts(0, 20))
                .thenReturn(concertPage);
        log.info("Mock 서비스 설정 완료");

        // when & then
        log.info("API 호출 시작: GET /api/concerts/available-dates?page=0&size=20");

        mockMvc.perform(get("/api/concerts/available-dates")
                        .param("page", "0")
                        .param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())  // 요청/응답 전체 출력
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("콘서트 목록 조회 성공"))
                .andExpect(jsonPath("$.data.concerts").isArray())
                .andExpect(jsonPath("$.data.concerts[0].concertId").value(1))
                .andExpect(jsonPath("$.data.concerts[0].title").value("Concert 1"))
                .andExpect(jsonPath("$.data.concerts[1].concertId").value(2))
                .andExpect(jsonPath("$.data.concerts[1].title").value("Concert 2"))
                .andExpect(jsonPath("$.data.pagination.page").value(0))
                .andExpect(jsonPath("$.data.pagination.size").value(20))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2))
                .andExpect(jsonPath("$.data.pagination.totalPages").value(1));

        log.info("✓ 테스트 완료: 모든 검증 통과");
        log.info("=== 테스트 종료 ===");
    }

    @Test
    @DisplayName("콘서트 상세 조회 API 테스트")
    void getConcertById_ShouldReturnConcertDetails() throws Exception {
        // given
        log.info("=== 테스트 시작: 콘서트 상세 조회 ===");

        Long concertId = 1L;
        ConcertResponseDto concert = createTestConcertDto(concertId, "Test Concert", "Test Artist");
        log.info("테스트 콘서트 생성: ID={}, Title={}, Artist={}",
                concert.getConcertId(), concert.getTitle(), concert.getArtist());

        when(concertService.getConcertById(concertId))
                .thenReturn(concert);
        log.info("Mock 서비스 설정 완료: getConcertById({})", concertId);

        // when & then
        log.info("API 호출 시작: GET /api/concerts/{}", concertId);

        mockMvc.perform(get("/api/concerts/{concertId}", concertId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("콘서트 조회 성공"))
                .andExpect(jsonPath("$.data.concertId").value(concertId))
                .andExpect(jsonPath("$.data.title").value("Test Concert"))
                .andExpect(jsonPath("$.data.artist").value("Test Artist"));

        log.info("✓ 테스트 완료: 콘서트 상세 조회 성공");
        log.info("=== 테스트 종료 ===");
    }

    @Test
    @DisplayName("존재하지 않는 콘서트 조회 시 404 에러 반환")
    void getConcertById_NotFound_ShouldReturn404() throws Exception {
        // given
        log.info("=== 테스트 시작: 존재하지 않는 콘서트 조회 ===");

        Long concertId = 999L;
        log.info("존재하지 않는 콘서트 ID: {}", concertId);

        when(concertService.getConcertById(concertId))
                .thenThrow(new ConcertNotFoundException("콘서트를 찾을 수 없습니다: " + concertId));
        log.info("Mock 서비스 설정 완료: ConcertNotFoundException 발생하도록 설정");

        // when & then
        log.info("API 호출 시작: GET /api/concerts/{} (404 에러 예상)", concertId);

        mockMvc.perform(get("/api/concerts/{concertId}", concertId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.error.type").value("CONCERT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("콘서트를 찾을 수 없습니다: " + concertId));

        log.info("✓ 테스트 완료: 404 에러 응답 정상 확인");
        log.info("=== 테스트 종료 ===");
    }

    @Test
    @DisplayName("특정 날짜 콘서트 조회 API 테스트")
    void getConcertsByDate_ShouldReturnConcertsForDate() throws Exception {
        // given
        log.info("=== 테스트 시작: 특정 날짜 콘서트 조회 ===");

        LocalDate targetDate = LocalDate.of(2025, 6, 1);
        log.info("조회 대상 날짜: {}", targetDate);

        ConcertResponseDto concert1 = createTestConcertDto(1L, "Concert 1", "Artist 1");
        ConcertResponseDto concert2 = createTestConcertDto(2L, "Concert 2", "Artist 2");
        List<ConcertResponseDto> concerts = Arrays.asList(concert1, concert2);
        log.info("해당 날짜 콘서트 {}개 준비 완료", concerts.size());

        when(concertService.getConcertsByDate(targetDate))
                .thenReturn(concerts);
        log.info("Mock 서비스 설정 완료: getConcertsByDate({})", targetDate);

        // when & then
        log.info("API 호출 시작: GET /api/concerts/by-date?date={}", targetDate);

        mockMvc.perform(get("/api/concerts/by-date")
                        .param("date", "2025-06-01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("날짜별 콘서트 조회 성공"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].concertId").value(1))
                .andExpect(jsonPath("$.data[1].concertId").value(2));

        log.info("✓ 테스트 완료: 날짜별 콘서트 조회 성공");
        log.info("=== 테스트 종료 ===");
    }

    @Test
    @DisplayName("아티스트 검색 API 테스트")
    void searchConcertsByArtist_ShouldReturnMatchingConcerts() throws Exception {
        // given
        log.info("=== 테스트 시작: 아티스트 검색 ===");

        String artistName = "IU";
        log.info("검색할 아티스트: {}", artistName);

        ConcertResponseDto concert1 = createTestConcertDto(1L, "IU Concert 1", "IU");
        ConcertResponseDto concert2 = createTestConcertDto(2L, "IU Concert 2", "IU");
        List<ConcertResponseDto> concerts = Arrays.asList(concert1, concert2);
        log.info("검색 결과 콘서트 {}개 준비 완료", concerts.size());

        when(concertService.searchConcertsByArtist(artistName))
                .thenReturn(concerts);
        log.info("Mock 서비스 설정 완료: searchConcertsByArtist({})", artistName);

        // when & then
        log.info("API 호출 시작: GET /api/concerts/search?artist={}", artistName);

        mockMvc.perform(get("/api/concerts/search")
                        .param("artist", artistName)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("아티스트별 콘서트 검색 성공"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].artist").value("IU"))
                .andExpect(jsonPath("$.data[1].artist").value("IU"));

        log.info("✓ 테스트 완료: 아티스트 검색 성공");
        log.info("=== 테스트 종료 ===");
    }

    private ConcertResponseDto createTestConcertDto(Long id, String title, String artist) {
        log.debug("createTestConcertDto 호출: ID={}, Title={}, Artist={}", id, title, artist);

        ConcertResponseDto dto = new ConcertResponseDto();
        try {
            setField(dto, "concertId", id);
            setField(dto, "title", title);
            setField(dto, "artist", artist);
            setField(dto, "venue", "Test Venue");
            setField(dto, "concertDate", LocalDate.of(2025, 6, 1));
            setField(dto, "concertTime", LocalTime.of(19, 0));
            setField(dto, "totalSeats", 50);
            setField(dto, "availableSeats", 50);
            setField(dto, "minPrice", 50000);
            setField(dto, "maxPrice", 150000);

            log.debug("DTO 생성 완료: {}", dto.getTitle());
        } catch (Exception e) {
            log.error("DTO 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Test setup failed", e);
        }
        return dto;
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}