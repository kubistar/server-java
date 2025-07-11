package kr.hhplus.be.server.ranking.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConcertRanking {

    private int rank;
    private Long concertId;
    private String concertName;
    private Double score;
    private RankingType type;
    private LocalDateTime updatedAt;

    // 매진 관련 정보
    private Integer soldOutDurationMinutes;
    private Double bookingSpeedPerMinute;
    private String status;

    public static ConcertRanking createSoldOutRanking(Long concertId, String concertName,
                                                      int soldOutDurationMinutes, double score) {
        return ConcertRanking.builder()
                .concertId(concertId)
                .concertName(concertName)
                .soldOutDurationMinutes(soldOutDurationMinutes)
                .score(score)
                .type(RankingType.SOLDOUT_SPEED)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public static ConcertRanking createBookingSpeedRanking(Long concertId, String concertName,
                                                           double bookingSpeedPerMinute, double score) {
        return ConcertRanking.builder()
                .concertId(concertId)
                .concertName(concertName)
                .bookingSpeedPerMinute(bookingSpeedPerMinute)
                .score(score)
                .type(RankingType.BOOKING_SPEED)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public boolean isSoldOut() {
        return soldOutDurationMinutes != null && soldOutDurationMinutes > 0;
    }

    public String getScoreDescription() {
        return switch (type) {
            case SOLDOUT_SPEED -> soldOutDurationMinutes + "분 만에 매진";
            case BOOKING_SPEED -> String.format("%.1f건/분", bookingSpeedPerMinute);
            case POPULARITY -> String.format("인기도 %.1f점", score);
        };
    }
}