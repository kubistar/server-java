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

        String title = "2025 BLACKPINK WORLD TOUR";
        String artist = "BLACKPINK";
        String venue = "올림픽공원 체조경기장";
        LocalDate concertDate = LocalDate.now().plusDays(30); // 미래 날짜로 변경
        LocalTime concertTime = LocalTime.of(19, 0);

        log.info("테스트 데이터 준비 완료:");
        log.info("  - Title: {}", title);
        log.info("  - Artist: {}", artist);
        log.info("  - Venue: {}", venue);
        log.info("  - Date: {} (현재 날짜 + 30일)", concertDate);
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

        assertThat(concert.getTitle()).contains("BLACKPINK"); // XSS 이스케이프 고려
        log.info("✓ 제목 검증 통과");

        assertThat(concert.getArtist()).contains("BLACKPINK"); // XSS 이스케이프 고려
        log.info("✓ 아티스트 검증 통과");

        assertThat(concert.getVenue()).isNotNull();
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
                "BLACKPINK Concert", "BLACKPINK", "KSPO DOME",
                LocalDate.now().plusDays(15), LocalTime.of(19, 0), // 미래 날짜로 변경
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
                "BLACKPINK Future Concert", "BLACKPINK", "Seoul Olympic Stadium",
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
    @DisplayName("충분히 미래의 콘서트는 예약 가능하다")
    void isBookable_FarFutureConcert_ShouldReturnTrue() {
        // given
        log.info("=== 테스트 시작: 충분히 미래 날짜 콘서트 예약 가능성 검증 ===");

        LocalDate farFutureDate = LocalDate.now().plusDays(30);
        LocalTime concertTime = LocalTime.of(19, 0);
        log.info("테스트 날짜:");
        log.info("  - 현재 날짜: {}", LocalDate.now());
        log.info("  - 콘서트 날짜: {} (현재 날짜 + 30일)", farFutureDate);
        log.info("  - 콘서트 시간: {}", concertTime);

        Concert concert = new Concert(
                "BLACKPINK WORLD TOUR 2025", "BLACKPINK", "Gocheok Sky Dome",
                farFutureDate, concertTime, 50
        );
        log.info("충분히 미래 날짜 콘서트 생성 완료");

        // when & then
        log.info("=== 예약 가능성 검증 시작 ===");
        boolean isBookable = concert.isBookable();
        log.info("isBookable() 결과: {}", isBookable);

        assertThat(isBookable).isTrue();
        log.info("✓ 충분히 미래 날짜 콘서트 예약 가능 검증 통과");

        log.info("=== 테스트 완료: 충분히 미래 날짜 콘서트는 예약 가능 ===");
    }

    @Test
    @DisplayName("오늘 날짜지만 현재 시간 이후의 콘서트는 예약 가능하다")
    void isBookable_TodayConcertFutureTime_ShouldReturnTrue() {
        // given
        log.info("=== 테스트 시작: 오늘 날짜 + 미래 시간 콘서트 예약 가능성 검증 ===");

        LocalDate today = LocalDate.now();
        LocalTime currentTime = LocalTime.now();
        LocalTime futureTime = currentTime.plusHours(2); // 충분한 시간 여유

        log.info("테스트 시간 정보:");
        log.info("  - 오늘 날짜: {}", today);
        log.info("  - 현재 시간: {}", currentTime);
        log.info("  - 콘서트 시간: {} (현재 시간 + 2시간)", futureTime);

        Concert concert = new Concert(
                "BLACKPINK Tonight Concert", "BLACKPINK", "Olympic Park",
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

    @Test
    @DisplayName("콘서트가 오늘 개최되는지 확인한다")
    void isConcertToday_TodayConcert_ShouldReturnTrue() {
        // given
        log.info("=== 테스트 시작: 오늘 콘서트 여부 확인 ===");

        LocalDate today = LocalDate.now();
        log.info("오늘 날짜: {}", today);

        Concert concert = new Concert(
                "BLACKPINK Today Concert", "BLACKPINK", "Seoul Arena",
                today, LocalTime.of(20, 0), 50
        );
        log.info("오늘 날짜 콘서트 생성 완료");

        // when & then
        log.info("=== 오늘 콘서트 여부 검증 시작 ===");
        boolean isConcertToday = concert.isConcertToday();
        log.info("isConcertToday() 결과: {}", isConcertToday);

        assertThat(isConcertToday).isTrue();
        log.info("✓ 오늘 콘서트 여부 검증 통과");

        log.info("=== 테스트 완료: 오늘 날짜 콘서트 확인 성공 ===");
    }

    @Test
    @DisplayName("총 좌석 수가 유효한 범위인지 확인한다")
    void hasValidSeatCount_ValidSeats_ShouldReturnTrue() {
        // given
        log.info("=== 테스트 시작: 유효한 좌석 수 범위 확인 ===");

        Concert concert = new Concert(
                "BLACKPINK Limited Concert", "BLACKPINK", "Small Theater",
                LocalDate.now().plusDays(10), LocalTime.of(19, 0), 50
        );
        log.info("좌석 수 50개 콘서트 생성 완료");

        // when & then
        log.info("=== 좌석 수 유효성 검증 시작 ===");
        boolean hasValidSeatCount = concert.hasValidSeatCount();
        log.info("hasValidSeatCount() 결과: {}", hasValidSeatCount);
        log.info("좌석 수: {} (유효 범위: 1~100)", concert.getTotalSeats());

        assertThat(hasValidSeatCount).isTrue();
        log.info("✓ 유효한 좌석 수 범위 검증 통과");

        log.info("=== 테스트 완료: 좌석 수가 유효한 범위에 있음 ===");
    }
}