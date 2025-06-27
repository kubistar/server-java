package kr.hhplus.be.server.concert.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    // 생성자
    public Concert(String title, String artist, String venue,
                   LocalDate concertDate, LocalTime concertTime, Integer totalSeats) {
        validateConcertData(title, artist, venue, concertDate, concertTime, totalSeats);

        this.title = title;
        this.artist = artist;
        this.venue = venue;
        this.concertDate = concertDate;
        this.concertTime = concertTime;
        this.totalSeats = totalSeats != null ? totalSeats : 50;
        this.createdAt = LocalDateTime.now();
    }

    // 편의 생성자 (totalSeats 기본값 50)
    public Concert(String title, String artist, String venue,
                   LocalDate concertDate, LocalTime concertTime) {
        this(title, artist, venue, concertDate, concertTime, 50);
    }

    // 비즈니스 메서드
    public boolean isBookable() {
        LocalDateTime concertDateTime = concertDate.atTime(concertTime);
        return concertDateTime.isAfter(LocalDateTime.now());
    }

    public boolean isConcertToday() {
        return concertDate.equals(LocalDate.now());
    }

    public boolean isPastConcert() {
        LocalDateTime concertDateTime = concertDate.atTime(concertTime);
        return concertDateTime.isBefore(LocalDateTime.now());
    }

    public boolean hasValidSeatCount() {
        return totalSeats != null && totalSeats > 0 && totalSeats <= 100;
    }

    // 유효성 검증 메서드
    private void validateConcertData(String title, String artist, String venue,
                                     LocalDate concertDate, LocalTime concertTime, Integer totalSeats) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("콘서트 제목은 필수입니다.");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("콘서트 제목은 200자를 초과할 수 없습니다.");
        }

        if (artist == null || artist.trim().isEmpty()) {
            throw new IllegalArgumentException("아티스트명은 필수입니다.");
        }
        if (artist.length() > 100) {
            throw new IllegalArgumentException("아티스트명은 100자를 초과할 수 없습니다.");
        }

        if (venue == null || venue.trim().isEmpty()) {
            throw new IllegalArgumentException("공연장명은 필수입니다.");
        }
        if (venue.length() > 200) {
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