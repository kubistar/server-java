package kr.hhplus.be.server.seat.service;

import kr.hhplus.be.server.seat.dto.SeatPageResponse;
import kr.hhplus.be.server.seat.dto.SeatResponseDto;
import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.exception.ConcertNotFoundException;
import kr.hhplus.be.server.concert.repository.ConcertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 좌석 관리 서비스
 */
@Service
@Transactional(readOnly = true)
public class SeatService {

    private static final Logger log = LoggerFactory.getLogger(SeatService.class);

    private final ConcertRepository concertRepository;

    public SeatService(ConcertRepository concertRepository) {
        this.concertRepository = concertRepository;
    }

    /**
     * 콘서트 좌석 정보 조회
     * @param concertId 콘서트 ID
     * @return 좌석 정보와 요약
     */
    public SeatPageResponse getConcertSeats(Long concertId) {
        log.info("콘서트 좌석 조회 시작: concertId={}", concertId);

        // 콘서트 정보 조회
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new ConcertNotFoundException("콘서트를 찾을 수 없습니다: " + concertId));

        log.info("콘서트 정보 조회 완료: title={}, totalSeats={}", concert.getTitle(), concert.getTotalSeats());

        // 좌석 정보 생성 (현재는 모든 좌석이 예약 가능한 상태로 생성)
        List<SeatResponseDto> seats = new ArrayList<>();
        int totalSeats = concert.getTotalSeats();

        for (int seatNumber = 1; seatNumber <= totalSeats; seatNumber++) {
            SeatResponseDto seat = SeatResponseDto.create(
                    (long) seatNumber,  // seatId (임시)
                    seatNumber,         // seatNumber
                    "AVAILABLE",        // status
                    50000,              // price (임시)
                    null,               // assignedUntil
                    null                // reservedAt
            );
            seats.add(seat);
        }

        // 좌석 요약 정보 생성
        SeatPageResponse.SeatSummary summary = new SeatPageResponse.SeatSummary(
                totalSeats,     // totalSeats
                totalSeats,     // availableSeats (현재는 모두 예약 가능)
                0,              // temporarilyAssignedSeats
                0               // reservedSeats
        );

        SeatPageResponse response = SeatPageResponse.builder()
                .concertId(concert.getConcertId())
                .concertTitle(concert.getTitle())
                .concertDate(concert.getConcertDate())
                .concertTime(concert.getConcertTime())
                .venue(concert.getVenue())
                .seats(seats)
                .summary(summary)
                .build();

        log.info("콘서트 좌석 조회 완료: concertId={}, totalSeats={}", concertId, totalSeats);
        return response;
    }
}