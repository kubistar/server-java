package kr.hhplus.be.server.concert.controller;

import kr.hhplus.be.server.concert.dto.ConcertResponseDto;
import kr.hhplus.be.server.concert.service.ConcertService;
import kr.hhplus.be.server.queue.service.QueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcertControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConcertService concertService;

    @MockitoBean
    private QueueService queueService;

    @Test
    @DisplayName("예약 가능한 콘서트 목록 조회 API 테스트")
    void getAvailableConcerts_ShouldReturnPagedConcerts() throws Exception {
        // given
        ConcertResponseDto concert1 = createTestConcertDto(1L, "Concert 1", "Artist 1");
        ConcertResponseDto concert2 = createTestConcertDto(2L, "Concert 2", "Artist 2");

        List<ConcertResponseDto> concerts = Arrays.asList(concert1, concert2);
        Page<ConcertResponseDto> concertPage = new PageImpl<>(concerts, PageRequest.of(0, 20), 2);

        when(concertService.getAvailableConcerts(0, 20))
                .thenReturn(concertPage);

        // when & then
        mockMvc.perform(get("/api/concerts/available-dates")
                        .param("page", "0")
                        .param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("콘서트 목록 조회 성공"))
                .andExpect(jsonPath("$.data.concerts").isArray())
                .andExpect(jsonPath("$.data.concerts[0].concertId").value(1))
                .andExpect(jsonPath("$.data.concerts[0].title").value("Concert 1"));
    }

    @Test
    @DisplayName("콘서트 상세 조회 API 테스트")
    void getConcertById_ShouldReturnConcertDetails() throws Exception {
        // given
        Long concertId = 1L;
        ConcertResponseDto concert = createTestConcertDto(concertId, "Test Concert", "Test Artist");

        when(concertService.getConcertById(concertId))
                .thenReturn(concert);

        // when & then
        mockMvc.perform(get("/api/concerts/{concertId}", concertId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("콘서트 조회 성공"))
                .andExpect(jsonPath("$.data.concertId").value(concertId))
                .andExpect(jsonPath("$.data.title").value("Test Concert"));
    }

    private ConcertResponseDto createTestConcertDto(Long id, String title, String artist) {
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
        } catch (Exception e) {
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