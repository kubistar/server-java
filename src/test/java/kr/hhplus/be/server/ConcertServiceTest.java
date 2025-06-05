package kr.hhplus.be.server;

import kr.hhplus.be.server.domain.Concert;
import kr.hhplus.be.server.dto.ConcertResponseDto;
import kr.hhplus.be.server.repository.ConcertRepository;
import kr.hhplus.be.server.service.ConcertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcertServiceTest {

    private static final Logger log = LoggerFactory.getLogger(ConcertServiceTest.class);

    @Mock
    private ConcertRepository concertRepository;

    private ConcertService concertService;

    @BeforeEach
    void setUp() {
        concertService = new ConcertService(concertRepository);
    }

    @Test
    @DisplayName("예약 가능한 콘서트 목록을 페이징으로 조회한다")
    void getAvailableConcerts_ShouldReturnPagedResult() {
        // given
        log.info("=== 테스트 시작: 예약 가능한 콘서트 목록 조회 ===");

        Concert concert1 = createTestConcert(1L, "Concert 1", "Artist 1");
        Concert concert2 = createTestConcert(2L, "Concert 2", "Artist 2");
        List<Concert> concerts = Arrays.asList(concert1, concert2);
        Page<Concert> concertPage = new PageImpl<>(concerts, PageRequest.of(0, 20), 2);

        log.info("Mock 데이터 준비 완료 - 총 {}개 콘서트", concerts.size());

        when(concertRepository.findAvailableConcerts(any(Pageable.class)))
                .thenReturn(concertPage);
        log.info("Mock 설정 완료");

        // when
        log.info("서비스 메서드 호출: getAvailableConcerts(0, 20)");
        Page<ConcertResponseDto> result = concertService.getAvailableConcerts(0, 20);

        // then
        log.info("결과 검증 시작 - 반환된 콘서트 수: {}", result.getContent().size());

        assertThat(result.getContent()).hasSize(2);
        log.info("✓ 사이즈 검증 통과");

        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Concert 1");
        log.info("✓ 첫 번째 콘서트 제목 검증 통과: {}", result.getContent().get(0).getTitle());

        assertThat(result.getContent().get(1).getTitle()).isEqualTo("Concert 2");
        log.info("✓ 두 번째 콘서트 제목 검증 통과: {}", result.getContent().get(1).getTitle());

        assertThat(result.getTotalElements()).isEqualTo(2);
        log.info("✓ 총 요소 수 검증 통과: {}", result.getTotalElements());

        verify(concertRepository).findAvailableConcerts(any(Pageable.class));
        log.info("✓ Repository 메서드 호출 검증 통과");

        log.info("=== 테스트 완료: 모든 검증 통과 ===");
    }

    @Test
    @DisplayName("콘서트 ID로 조회 시 존재하는 콘서트를 반환한다")
    void getConcertById_ExistingConcert_ShouldReturnConcert() {
        // given
        Long concertId = 1L;
        Concert concert = createTestConcert(concertId, "Test Concert", "Test Artist");

        when(concertRepository.findById(concertId))
                .thenReturn(Optional.of(concert));

        // when
        ConcertResponseDto result = concertService.getConcertById(concertId);

        // then
        assertThat(result.getConcertId()).isEqualTo(concertId);
        assertThat(result.getTitle()).isEqualTo("Test Concert");
        assertThat(result.getArtist()).isEqualTo("Test Artist");

        verify(concertRepository).findById(concertId);
    }

    @Test
    @DisplayName("존재하지 않는 콘서트 ID로 조회 시 예외를 발생시킨다")
    void getConcertById_NonExistingConcert_ShouldThrowException() {
        // given
        Long concertId = 999L;

        when(concertRepository.findById(concertId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> concertService.getConcertById(concertId))
                .isInstanceOf(ConcertNotFoundException.class)
                .hasMessage("콘서트를 찾을 수 없습니다: " + concertId);

        verify(concertRepository).findById(concertId);
    }

    @Test
    @DisplayName("특정 날짜의 콘서트 목록을 조회한다")
    void getConcertsByDate_ShouldReturnConcertsForSpecificDate() {
        // given
        LocalDate targetDate = LocalDate.of(2025, 6, 1);
        Concert concert1 = createTestConcert(1L, "Concert 1", "Artist 1");
        Concert concert2 = createTestConcert(2L, "Concert 2", "Artist 2");
        List<Concert> concerts = Arrays.asList(concert1, concert2);

        when(concertRepository.findByConcertDate(targetDate))
                .thenReturn(concerts);

        // when
        List<ConcertResponseDto> result = concertService.getConcertsByDate(targetDate);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("Concert 1");
        assertThat(result.get(1).getTitle()).isEqualTo("Concert 2");

        verify(concertRepository).findByConcertDate(targetDate);
    }

    @Test
    @DisplayName("아티스트명으로 콘서트를 검색한다")
    void searchConcertsByArtist_ShouldReturnMatchingConcerts() {
        // given
        String artistName = "IU";
        Concert concert1 = createTestConcert(1L, "IU Concert 1", "IU");
        Concert concert2 = createTestConcert(2L, "IU Concert 2", "IU");
        List<Concert> concerts = Arrays.asList(concert1, concert2);

        when(concertRepository.findByArtistContaining(artistName))
                .thenReturn(concerts);

        // when
        List<ConcertResponseDto> result = concertService.searchConcertsByArtist(artistName);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getArtist()).isEqualTo("IU");
        assertThat(result.get(1).getArtist()).isEqualTo("IU");

        verify(concertRepository).findByArtistContaining(artistName);
    }

    private Concert createTestConcert(Long id, String title, String artist) {
        log.debug("createTestConcert 호출 - ID: {}, Title: {}, Artist: {}", id, title, artist);

        Concert concert = new Concert(
                title,
                artist,
                "Test Venue",
                LocalDate.of(2025, 6, 1),
                LocalTime.of(19, 0),
                50
        );

        // Reflection을 사용하여 ID 설정 (테스트용)
        try {
            java.lang.reflect.Field idField = Concert.class.getDeclaredField("concertId");
            idField.setAccessible(true);
            idField.set(concert, id);
            log.debug("Reflection으로 ID 설정 완료: {}", id);
        } catch (Exception e) {
            log.error("Reflection ID 설정 실패: {}", e.getMessage());
            throw new RuntimeException("Test setup failed", e);
        }

        log.debug("콘서트 생성 완료 - ID: {}, Title: {}", concert.getConcertId(), concert.getTitle());
        return concert;
    }
}