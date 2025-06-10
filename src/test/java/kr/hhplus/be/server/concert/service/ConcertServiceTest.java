package kr.hhplus.be.server.concert.service;

import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.dto.ConcertResponseDto;
import kr.hhplus.be.server.concert.exception.ConcertNotFoundException;
import kr.hhplus.be.server.concert.repository.ConcertRepository;
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
        log.info("=== 테스트 준비: ConcertService 초기화 ===");
        concertService = new ConcertService(concertRepository);
        log.info("ConcertService 인스턴스 생성 완료");
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
        log.info("페이징 정보: page=0, size=20, totalElements=2");

        when(concertRepository.findAvailableConcerts(any(Pageable.class)))
                .thenReturn(concertPage);
        log.info("Mock Repository 설정 완료: findAvailableConcerts() 호출 시 {} 개 콘서트 반환", concerts.size());

        // when
        log.info("서비스 메서드 호출: concertService.getAvailableConcerts(0, 20)");
        Page<ConcertResponseDto> result = concertService.getAvailableConcerts(0, 20);
        log.info("서비스 메서드 호출 완료");

        // then
        log.info("=== 검증 시작 ===");
        log.info("반환된 콘서트 수: {}", result.getContent().size());
        log.info("총 요소 수: {}", result.getTotalElements());
        log.info("페이지 번호: {}, 페이지 크기: {}", result.getNumber(), result.getSize());

        assertThat(result.getContent()).hasSize(2);
        log.info("✓ 사이즈 검증 통과: 2개");

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
        log.info("=== 테스트 시작: 콘서트 ID로 조회 (존재하는 콘서트) ===");

        Long concertId = 1L;
        log.info("조회할 콘서트 ID: {}", concertId);

        Concert concert = createTestConcert(concertId, "Test Concert", "Test Artist");
        log.info("테스트 콘서트 생성 완료: ID={}, Title={}", concert.getConcertId(), concert.getTitle());

        when(concertRepository.findById(concertId))
                .thenReturn(Optional.of(concert));
        log.info("Mock Repository 설정 완료: findById({}) 호출 시 콘서트 반환", concertId);

        // when
        log.info("서비스 메서드 호출: concertService.getConcertById({})", concertId);
        ConcertResponseDto result = concertService.getConcertById(concertId);
        log.info("서비스 메서드 호출 완료");

        // then
        log.info("=== 검증 시작 ===");
        log.info("반환된 콘서트 정보: ID={}, Title={}, Artist={}",
                result.getConcertId(), result.getTitle(), result.getArtist());

        assertThat(result.getConcertId()).isEqualTo(concertId);
        log.info("✓ 콘서트 ID 검증 통과: {}", result.getConcertId());

        assertThat(result.getTitle()).isEqualTo("Test Concert");
        log.info("✓ 콘서트 제목 검증 통과: {}", result.getTitle());

        assertThat(result.getArtist()).isEqualTo("Test Artist");
        log.info("✓ 아티스트명 검증 통과: {}", result.getArtist());

        verify(concertRepository).findById(concertId);
        log.info("✓ Repository 메서드 호출 검증 통과");

        log.info("=== 테스트 완료: 모든 검증 통과 ===");
    }

    @Test
    @DisplayName("존재하지 않는 콘서트 ID로 조회 시 예외를 발생시킨다")
    void getConcertById_NonExistingConcert_ShouldThrowException() {
        // given
        log.info("=== 테스트 시작: 존재하지 않는 콘서트 ID로 조회 ===");

        Long concertId = 999L;
        log.info("존재하지 않는 콘서트 ID: {}", concertId);

        when(concertRepository.findById(concertId))
                .thenReturn(Optional.empty());
        log.info("Mock Repository 설정 완료: findById({}) 호출 시 Optional.empty() 반환", concertId);

        // when & then
        log.info("서비스 메서드 호출 및 예외 검증: concertService.getConcertById({})", concertId);

        assertThatThrownBy(() -> concertService.getConcertById(concertId))
                .isInstanceOf(ConcertNotFoundException.class)
                .hasMessage("콘서트를 찾을 수 없습니다: " + concertId);

        log.info("✓ ConcertNotFoundException 발생 검증 통과");
        log.info("✓ 예외 메시지 검증 통과: '콘서트를 찾을 수 없습니다: {}'", concertId);

        verify(concertRepository).findById(concertId);
        log.info("✓ Repository 메서드 호출 검증 통과");

        log.info("=== 테스트 완료: 예외 처리 검증 통과 ===");
    }

    @Test
    @DisplayName("특정 날짜의 콘서트 목록을 조회한다")
    void getConcertsByDate_ShouldReturnConcertsForSpecificDate() {
        // given
        log.info("=== 테스트 시작: 특정 날짜의 콘서트 목록 조회 ===");

        LocalDate targetDate = LocalDate.of(2025, 6, 1);
        log.info("조회 대상 날짜: {}", targetDate);

        Concert concert1 = createTestConcert(1L, "Concert 1", "Artist 1");
        Concert concert2 = createTestConcert(2L, "Concert 2", "Artist 2");
        List<Concert> concerts = Arrays.asList(concert1, concert2);
        log.info("해당 날짜 콘서트 {}개 준비 완료", concerts.size());

        when(concertRepository.findByConcertDate(targetDate))
                .thenReturn(concerts);
        log.info("Mock Repository 설정 완료: findByConcertDate({}) 호출 시 {}개 콘서트 반환", targetDate, concerts.size());

        // when
        log.info("서비스 메서드 호출: concertService.getConcertsByDate({})", targetDate);
        List<ConcertResponseDto> result = concertService.getConcertsByDate(targetDate);
        log.info("서비스 메서드 호출 완료");

        // then
        log.info("=== 검증 시작 ===");
        log.info("반환된 콘서트 수: {}", result.size());

        assertThat(result).hasSize(2);
        log.info("✓ 콘서트 수 검증 통과: {}개", result.size());

        assertThat(result.get(0).getTitle()).isEqualTo("Concert 1");
        log.info("✓ 첫 번째 콘서트 제목 검증 통과: {}", result.get(0).getTitle());

        assertThat(result.get(1).getTitle()).isEqualTo("Concert 2");
        log.info("✓ 두 번째 콘서트 제목 검증 통과: {}", result.get(1).getTitle());

        verify(concertRepository).findByConcertDate(targetDate);
        log.info("✓ Repository 메서드 호출 검증 통과");

        log.info("=== 테스트 완료: 모든 검증 통과 ===");
    }

    @Test
    @DisplayName("아티스트명으로 콘서트를 검색한다")
    void searchConcertsByArtist_ShouldReturnMatchingConcerts() {
        // given
        log.info("=== 테스트 시작: 아티스트명으로 콘서트 검색 ===");

        String artistName = "IU";
        log.info("검색할 아티스트: {}", artistName);

        Concert concert1 = createTestConcert(1L, "IU Concert 1", "IU");
        Concert concert2 = createTestConcert(2L, "IU Concert 2", "IU");
        List<Concert> concerts = Arrays.asList(concert1, concert2);
        log.info("검색 결과 콘서트 {}개 준비 완료", concerts.size());

        when(concertRepository.findByArtistContaining(artistName))
                .thenReturn(concerts);
        log.info("Mock Repository 설정 완료: findByArtistContaining('{}') 호출 시 {}개 콘서트 반환", artistName, concerts.size());

        // when
        log.info("서비스 메서드 호출: concertService.searchConcertsByArtist('{}')", artistName);
        List<ConcertResponseDto> result = concertService.searchConcertsByArtist(artistName);
        log.info("서비스 메서드 호출 완료");

        // then
        log.info("=== 검증 시작 ===");
        log.info("검색된 콘서트 수: {}", result.size());

        assertThat(result).hasSize(2);
        log.info("✓ 검색 결과 수 검증 통과: {}개", result.size());

        assertThat(result.get(0).getArtist()).isEqualTo("IU");
        log.info("✓ 첫 번째 콘서트 아티스트 검증 통과: {}", result.get(0).getArtist());

        assertThat(result.get(1).getArtist()).isEqualTo("IU");
        log.info("✓ 두 번째 콘서트 아티스트 검증 통과: {}", result.get(1).getArtist());

        verify(concertRepository).findByArtistContaining(artistName);
        log.info("✓ Repository 메서드 호출 검증 통과");

        log.info("=== 테스트 완료: 모든 검증 통과 ===");
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
        log.debug("Concert 엔티티 생성 완료");

        // Reflection을 사용하여 ID 설정 (테스트용)
        try {
            java.lang.reflect.Field idField = Concert.class.getDeclaredField("concertId");
            idField.setAccessible(true);
            idField.set(concert, id);
            log.debug("Reflection으로 ID 설정 완료: {}", id);
        } catch (Exception e) {
            log.error("Reflection ID 설정 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Test setup failed", e);
        }

        log.debug("콘서트 생성 완료 - ID: {}, Title: {}", concert.getConcertId(), concert.getTitle());
        return concert;
    }
}