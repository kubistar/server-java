package kr.hhplus.be.server.concert;

import kr.hhplus.be.server.concert.domain.Concert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Concert 엔티티 단위 테스트
 * 도메인 객체의 비즈니스 로직과 생성 규칙을 검증
 */
public class ConcertTest {

    private static final Logger log = LoggerFactory.getLogger(ConcertTest.class);

    @Test
    @DisplayName("콘서트 생성 시 기본값이 올바르게 설정된다")
    void createConcert_ShouldSetDefaultValues() {
        // given
        log.info("=== 테스트 시작: 콘서트 생성 시 기본값 설정 검증 ===");

        String title = "2025 Spring Concert";
        String artist = "IU";
        String venue = "올림픽공원 체조경기장";
        LocalDate concertDate = LocalDate.of(2025, 6, 1);
        LocalTime concertTime = LocalTime.of(19, 0);

        log.info("테스트 데이터 준비 완료:");
        log.info("  - Title: {}", title);
        log.info("  - Artist: {}", artist);
        log.info("  - Venue: {}", venue);
        log.info("  - Date: {}", concertDate);
        log.info("  - Time: {}", concertTime);
        log.info("  - TotalSeats: null (기본값 50 적용 예상)");

        // when
        log.info("Concert 엔티티 생성 시작");
        Concert concert = new Concert(title, artist, venue, concertDate, concertTime, null);
        log.info("Concert 엔티티 생성 완료");

        // then
        log.info("=== 검증 시작 ===");
        log.info("생성된 콘서트 정보:");
        log.info("  - Title: {}", concert.getTitle());
        log.info("  - Artist: {}", concert.getArtist());
        log.info("  - Venue: {}", concert.getVenue());
        log.info("  - Date: {}", concert.getConcertDate());
        log.info("  - Time: {}", concert.getConcertTime());
        log.info("  - TotalSeats: {}", concert.getTotalSeats());
        log.info("  - CreatedAt: {}", concert.getCreatedAt());

        assertThat(concert.getTitle()).isEqualTo(title);
        log.info("✓ 제목 검증 통과");

        assertThat(concert.getArtist()).isEqualTo(artist);
        log.info("✓ 아티스트 검증 통과");

        assertThat(concert.getVenue()).isEqualTo(venue);
        log.info("✓ 공연장 검증 통과");

        assertThat(concert.getConcertDate()).isEqualTo(concertDate);
        log.info("✓ 공연 날짜 검증 통과");

        assertThat(concert.getConcertTime()).isEqualTo(concertTime);
        log.info("✓ 공연 시간 검증 통과");

        assertThat(concert.getTotalSeats()).isEqualTo(50); // 기본값
        log.info("✓ 총 좌석 수 기본값(50) 검증 통과");

        assertThat(concert.getCreatedAt()).isNotNull();
        log.info("✓ 생성일시 자동 설정 검증 통과");

        log.info("=== 테스트 완료: 모든 기본값 설정 검증 통과 ===");
    }

    @Test
    @DisplayName("총 좌석 수를 명시적으로 설정할 수 있다")
    void createConcert_WithCustomTotalSeats() {
        // given
        log.info("=== 테스트 시작: 커스텀 좌석 수 설정 검증 ===");

        Integer customTotalSeats = 100;
        log.info("커스텀 좌석 수: {}", customTotalSeats);

        // when
        log.info("커스텀 좌석 수로 Concert 엔티티 생성 시작");
        Concert concert = new Concert(
                "Concert", "Artist", "Venue",
                LocalDate.of(2025, 6, 1), LocalTime.of(19, 0),
                customTotalSeats
        );
        log.info("Concert 엔티티 생성 완료");

        // then
        log.info("=== 검증 시작 ===");
        log.info("실제 설정된 좌석 수: {}", concert.getTotalSeats());

        assertThat(concert.getTotalSeats()).isEqualTo(customTotalSeats);
        log.info("✓ 커스텀 좌석 수({}) 설정 검증 통과", customTotalSeats);

        log.info("=== 테스트 완료: 커스텀 좌석 수 설정 검증 통과 ===");
    }

    @Test
    @DisplayName("현재 날짜 이후의 콘서트는 예약 가능하다")
    void isBookable_FutureConcert_ShouldReturnTrue() {
        // given
        log.info("=== 테스트 시작: 미래 날짜 콘서트 예약 가능성 검증 ===");

        LocalDate futureDate = LocalDate.now().plusDays(1);
        LocalTime concertTime = LocalTime.of(19, 0);
        log.info("테스트 날짜:");
        log.info("  - 현재 날짜: {}", LocalDate.now());
        log.info("  - 콘서트 날짜: {} (현재 날짜 + 1일)", futureDate);
        log.info("  - 콘서트 시간: {}", concertTime);

        Concert concert = new Concert(
                "Future Concert", "Artist", "Venue",
                futureDate, concertTime, 50
        );
        log.info("미래 날짜 콘서트 생성 완료");

        // when & then
        log.info("=== 예약 가능성 검증 시작 ===");
        boolean isBookable = concert.isBookable();
        log.info("isBookable() 결과: {}", isBookable);

        assertThat(isBookable).isTrue();
        log.info("✓ 미래 날짜 콘서트 예약 가능 검증 통과");

        log.info("=== 테스트 완료: 미래 날짜 콘서트는 예약 가능 ===");
    }

    @Test
    @DisplayName("과거 날짜의 콘서트는 예약 불가능하다")
    void isBookable_PastConcert_ShouldReturnFalse() {
        // given
        log.info("=== 테스트 시작: 과거 날짜 콘서트 예약 불가능 검증 ===");

        LocalDate pastDate = LocalDate.now().minusDays(1);
        LocalTime concertTime = LocalTime.of(19, 0);
        log.info("테스트 날짜:");
        log.info("  - 현재 날짜: {}", LocalDate.now());
        log.info("  - 콘서트 날짜: {} (현재 날짜 - 1일)", pastDate);
        log.info("  - 콘서트 시간: {}", concertTime);

        Concert concert = new Concert(
                "Past Concert", "Artist", "Venue",
                pastDate, concertTime, 50
        );
        log.info("과거 날짜 콘서트 생성 완료");

        // when & then
        log.info("=== 예약 가능성 검증 시작 ===");
        boolean isBookable = concert.isBookable();
        log.info("isBookable() 결과: {}", isBookable);

        assertThat(isBookable).isFalse();
        log.info("✓ 과거 날짜 콘서트 예약 불가능 검증 통과");

        log.info("=== 테스트 완료: 과거 날짜 콘서트는 예약 불가능 ===");
    }

    @Test
    @DisplayName("오늘 날짜지만 현재 시간 이후의 콘서트는 예약 가능하다")
    void isBookable_TodayConcertFutureTime_ShouldReturnTrue() {
        // given
        log.info("=== 테스트 시작: 오늘 날짜 + 미래 시간 콘서트 예약 가능성 검증 ===");

        LocalDate today = LocalDate.now();
        LocalTime currentTime = LocalTime.now();
        LocalTime futureTime = currentTime.plusHours(1);

        log.info("테스트 시간 정보:");
        log.info("  - 오늘 날짜: {}", today);
        log.info("  - 현재 시간: {}", currentTime);
        log.info("  - 콘서트 시간: {} (현재 시간 + 1시간)", futureTime);

        Concert concert = new Concert(
                "Today Concert", "Artist", "Venue",
                today, futureTime, 50
        );
        log.info("오늘 날짜 + 미래 시간 콘서트 생성 완료");

        // when & then
        log.info("=== 예약 가능성 검증 시작 ===");
        boolean isBookable = concert.isBookable();
        log.info("isBookable() 결과: {}", isBookable);
        log.info("검증 로직: 오늘 날짜이지만 현재 시간보다 늦은 시간이므로 예약 가능해야 함");

        assertThat(isBookable).isTrue();
        log.info("✓ 오늘 날짜 + 미래 시간 콘서트 예약 가능 검증 통과");

        log.info("=== 테스트 완료: 오늘 날짜라도 미래 시간이면 예약 가능 ===");
    }
}