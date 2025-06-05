package kr.hhplus.be.server.dto;


import kr.hhplus.be.server.domain.Concert;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
public class ConcertResponseDto {
    private Long concertId;
    private String title;
    private String artist;
    private String venue;
    private LocalDate concertDate;
    private LocalTime concertTime;
    private Integer totalSeats;
    private Integer availableSeats;
    private Integer minPrice;
    private Integer maxPrice;

    // 기본 생성자
    public ConcertResponseDto() {}

    // 콘서트 엔티티로부터 생성하는 정적 팩토리 메서드
    public static ConcertResponseDto from(Concert concert) {
        ConcertResponseDto dto = new ConcertResponseDto();
        dto.concertId = concert.getConcertId();
        dto.title = concert.getTitle();
        dto.artist = concert.getArtist();
        dto.venue = concert.getVenue();
        dto.concertDate = concert.getConcertDate();
        dto.concertTime = concert.getConcertTime();
        dto.totalSeats = concert.getTotalSeats();
        // TODO: 실제로는 좌석 조회해서 계산해야 함
        dto.availableSeats = concert.getTotalSeats();
        dto.minPrice = 50000; // TODO: 실제 좌석 가격에서 계산
        dto.maxPrice = 150000;
        return dto;
    }

}