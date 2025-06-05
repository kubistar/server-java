package kr.hhplus.be.server;

import kr.hhplus.be.server.domain.Concert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

// =============================================================================
// 단위 테스트들
// =============================================================================


public class ConcertTest {
    @Test
    @DisplayName("콘서트 생성 시 기본값이 올바르게 설정된다")
    void createConcert_ShouldSetDefaultValues() {
        // given
        String title = "2025 Spring Concert";
        String artist = "IU";
        String venue = "올림픽공원 체조경기장";
        LocalDate concertDate = LocalDate.of(2025, 6, 1);
        LocalTime concertTime = LocalTime.of(19, 0);

        // when
        Concert concert = new Concert(title, artist, venue, concertDate, concertTime, null);

        // then
        assertThat(concert.getTitle()).isEqualTo(title);
        assertThat(concert.getArtist()).isEqualTo(artist);
        assertThat(concert.getVenue()).isEqualTo(venue);
        assertThat(concert.getConcertDate()).isEqualTo(concertDate);
        assertThat(concert.getConcertTime()).isEqualTo(concertTime);
        assertThat(concert.getTotalSeats()).isEqualTo(50); // 기본값
        assertThat(concert.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("총 좌석 수를 명시적으로 설정할 수 있다")
    void createConcert_WithCustomTotalSeats() {
        // given
        Integer customTotalSeats = 100;

        // when
        Concert concert = new Concert(
                "Concert", "Artist", "Venue",
                LocalDate.of(2025, 6, 1), LocalTime.of(19, 0),
                customTotalSeats
        );

        // then
        assertThat(concert.getTotalSeats()).isEqualTo(customTotalSeats);
    }

    @Test
    @DisplayName("현재 날짜 이후의 콘서트는 예약 가능하다")
    void isBookable_FutureConcert_ShouldReturnTrue() {
        // given
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Concert concert = new Concert(
                "Future Concert", "Artist", "Venue",
                futureDate, LocalTime.of(19, 0), 50
        );

        // when & then
        assertThat(concert.isBookable()).isTrue();
    }

    @Test
    @DisplayName("과거 날짜의 콘서트는 예약 불가능하다")
    void isBookable_PastConcert_ShouldReturnFalse() {
        // given
        LocalDate pastDate = LocalDate.now().minusDays(1);
        Concert concert = new Concert(
                "Past Concert", "Artist", "Venue",
                pastDate, LocalTime.of(19, 0), 50
        );

        // when & then
        assertThat(concert.isBookable()).isFalse();
    }

    @Test
    @DisplayName("오늘 날짜지만 현재 시간 이후의 콘서트는 예약 가능하다")
    void isBookable_TodayConcertFutureTime_ShouldReturnTrue() {
        // given
        LocalDate today = LocalDate.now();
        LocalTime futureTime = LocalTime.now().plusHours(1);
        Concert concert = new Concert(
                "Today Concert", "Artist", "Venue",
                today, futureTime, 50
        );

        // when & then
        assertThat(concert.isBookable()).isTrue();
    }
}
