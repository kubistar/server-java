package kr.hhplus.be.server.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "concerts")
@Getter
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
    private Integer totalSeats = 50;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 기본 생성자 (JPA 필수)
    protected Concert() {}

    // 생성자
    public Concert(String title, String artist, String venue,
                   LocalDate concertDate, LocalTime concertTime, Integer totalSeats) {
        this.title = title;
        this.artist = artist;
        this.venue = venue;
        this.concertDate = concertDate;
        this.concertTime = concertTime;
        this.totalSeats = totalSeats != null ? totalSeats : 50;
        this.createdAt = LocalDateTime.now();
    }

    // 비즈니스 메서드
    public boolean isBookable() {
        return concertDate.isAfter(LocalDate.now()) ||
                (concertDate.equals(LocalDate.now()) && concertTime.isAfter(LocalTime.now()));
    }


}
