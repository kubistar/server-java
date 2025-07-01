package kr.hhplus.be.server.concert.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.text.StringEscapeUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Entity
@Table(name = "concerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Concert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "concert_id")
    private Long concertId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "artist", nullable = false, length = 100)
    private String artist;

    @Column(name = "venue", nullable = false, length = 200)
    private String venue;

    @Column(name = "concert_date", nullable = false)
    private LocalDate concertDate;

    @Column(name = "concert_time", nullable = false)
    private LocalTime concertTime;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 콘서트 정보를 생성
     * 생성 시간은 자동으로 현재 시간으로 설정
     *
     * @param title 콘서트 제목 (필수, 200자 이하)
     * @param artist 아티스트명 (필수, 100자 이하)
     * @param venue 공연장명 (필수, 200자 이하)
     * @param concertDate 공연 날짜 (필수, 현재 날짜 이후)
     * @param concertTime 공연 시간 (필수)
     * @param totalSeats 총 좌석 수 (1~100 사이, null인 경우 기본값 50)
     * @throws IllegalArgumentException 입력 데이터가 유효하지 않은 경우
     */
    public Concert(String title, String artist, String venue,
                   LocalDate concertDate, LocalTime concertTime, Integer totalSeats) {
        validateConcertData(title, artist, venue, concertDate, concertTime, totalSeats);

        // XSS 방어를 위한 HTML 이스케이프 처리
        this.title = StringEscapeUtils.escapeHtml4(title);
        this.artist = StringEscapeUtils.escapeHtml4(artist);
        this.venue = StringEscapeUtils.escapeHtml4(venue);
        this.concertDate = concertDate;
        this.concertTime = concertTime;
        this.totalSeats = totalSeats != null ? totalSeats : 50;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 콘서트 정보를 생성
     * 총 좌석 수는 기본값 50으로 설정
     *
     * @param title 콘서트 제목 (필수, 200자 이하)
     * @param artist 아티스트명 (필수, 100자 이하)
     * @param venue 공연장명 (필수, 200자 이하)
     * @param concertDate 공연 날짜 (필수, 현재 날짜 이후)
     * @param concertTime 공연 시간 (필수)
     * @throws IllegalArgumentException 입력 데이터가 유효하지 않은 경우
     */
    public Concert(String title, String artist, String venue,
                   LocalDate concertDate, LocalTime concertTime) {
        this(title, artist, venue, concertDate, concertTime, 50);
    }

    /**
     * 콘서트가 예약 가능한 상태인지 확인
     * 공연 일시가 현재 시간 이후인 경우에만 예약 가능
     *
     * @return 예약 가능하면 true, 불가능하면 false
     */
    public boolean isBookable() {
        LocalDateTime concertDateTime = concertDate.atTime(concertTime);
        return concertDateTime.isAfter(LocalDateTime.now());
    }

    /**
     * 콘서트가 오늘 개최되는지 확인
     *
     * @return 오늘 공연이면 true, 아니면 false
     */
    public boolean isConcertToday() {
        return concertDate.equals(LocalDate.now());
    }

    /**
     * 콘서트가 이미 지나간 공연인지 확인
     * 공연 일시가 현재 시간보다 이전인 경우 true를 반환
     *
     * @return 지나간 공연이면 true, 아니면 false
     */
    public boolean isPastConcert() {
        LocalDateTime concertDateTime = concertDate.atTime(concertTime);
        return concertDateTime.isBefore(LocalDateTime.now());
    }

    /**
     * 총 좌석 수가 유효한 범위인지 확인
     * 유효 범위: 1~100석
     *
     * @return 유효한 좌석 수이면 true, 아니면 false
     */
    public boolean hasValidSeatCount() {
        return totalSeats != null && totalSeats > 0 && totalSeats <= 100;
    }

    /**
     * 콘서트 데이터의 유효성을 검증
     * 검증 조건:
     * - 제목: 필수, 200자 이하
     * - 아티스트: 필수, 100자 이하
     * - 공연장: 필수, 200자 이하
     * - 공연 날짜: 필수, 현재 날짜 이후
     * - 공연 시간: 필수
     * - 총 좌석 수: 선택, 1~100 사이
     *
     * @param title 콘서트 제목
     * @param artist 아티스트명
     * @param venue 공연장명
     * @param concertDate 공연 날짜
     * @param concertTime 공연 시간
     * @param totalSeats 총 좌석 수
     * @throws IllegalArgumentException 유효하지 않은 데이터가 전달된 경우
     */
    private void validateConcertData(String title, String artist, String venue,
                                     LocalDate concertDate, LocalTime concertTime, Integer totalSeats) {
        // 제목 검증
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("콘서트 제목은 필수입니다.");
        }
        String escapedTitle = StringEscapeUtils.escapeHtml4(title);
        if (escapedTitle.length() > 200) {
            throw new IllegalArgumentException("콘서트 제목은 200자를 초과할 수 없습니다.");
        }

        // 아티스트 검증
        if (artist == null || artist.trim().isEmpty()) {
            throw new IllegalArgumentException("아티스트명은 필수입니다.");
        }
        String escapedArtist = StringEscapeUtils.escapeHtml4(artist);
        if (escapedArtist.length() > 100) {
            throw new IllegalArgumentException("아티스트명은 100자를 초과할 수 없습니다.");
        }

        // 공연장 검증
        if (venue == null || venue.trim().isEmpty()) {
            throw new IllegalArgumentException("공연장명은 필수입니다.");
        }
        String escapedVenue = StringEscapeUtils.escapeHtml4(venue);
        if (escapedVenue.length() > 200) {
            throw new IllegalArgumentException("공연장명은 200자를 초과할 수 없습니다.");
        }

        if (concertDate == null) {
            throw new IllegalArgumentException("공연 날짜는 필수입니다.");
        }
        if (concertDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("공연 날짜는 현재 날짜 이후여야 합니다.");
        }

        if (concertTime == null) {
            throw new IllegalArgumentException("공연 시간은 필수입니다.");
        }

        if (totalSeats != null && (totalSeats <= 0 || totalSeats > 100)) {
            throw new IllegalArgumentException("총 좌석 수는 1~100 사이여야 합니다.");
        }
    }
}