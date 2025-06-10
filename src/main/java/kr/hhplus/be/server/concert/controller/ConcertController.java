package kr.hhplus.be.server.concert.controller;

import kr.hhplus.be.server.common.ApiResponse;
import kr.hhplus.be.server.concert.dto.ConcertPageResponse;
import kr.hhplus.be.server.common.dto.PaginationResponse;
import kr.hhplus.be.server.concert.dto.ConcertResponseDto;
import kr.hhplus.be.server.concert.service.ConcertService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/concerts")
public class ConcertController {

    private final ConcertService concertService;

    public ConcertController(ConcertService concertService) {
        this.concertService = concertService;
    }

    /**
     * 예약 가능한 콘서트 날짜 목록 조회
     * GET /api/concerts/available-dates
     */
    @GetMapping("/available-dates")
    public ResponseEntity<ApiResponse<ConcertPageResponse>> getAvailableConcerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ConcertResponseDto> concerts = concertService.getAvailableConcerts(page, size);

        ConcertPageResponse response = ConcertPageResponse.builder()
                .concerts(concerts.getContent())
                .pagination(PaginationResponse.from(concerts))
                .build();

        return ResponseEntity.ok(
                ApiResponse.success(response, "콘서트 목록 조회 성공")
        );
    }

    /**
     * 콘서트 상세 조회
     * GET /api/concerts/{concertId}
     */
    @GetMapping("/{concertId}")
    public ResponseEntity<ApiResponse<ConcertResponseDto>> getConcertById(
            @PathVariable Long concertId) {

        ConcertResponseDto concert = concertService.getConcertById(concertId);
        return ResponseEntity.ok(
                ApiResponse.success(concert, "콘서트 조회 성공")
        );
    }

    /**
     * 특정 날짜의 콘서트 조회
     * GET /api/concerts/by-date?date=2025-06-01
     */
    @GetMapping("/by-date")
    public ResponseEntity<ApiResponse<List<ConcertResponseDto>>> getConcertsByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<ConcertResponseDto> concerts = concertService.getConcertsByDate(date);
        return ResponseEntity.ok(
                ApiResponse.success(concerts, "날짜별 콘서트 조회 성공")
        );
    }

    /**
     * 아티스트명으로 콘서트 검색
     * GET /api/concerts/search?artist=IU
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ConcertResponseDto>>> searchConcertsByArtist(
            @RequestParam String artist) {

        List<ConcertResponseDto> concerts = concertService.searchConcertsByArtist(artist);
        return ResponseEntity.ok(
                ApiResponse.success(concerts, "아티스트별 콘서트 검색 성공")
        );
    }
}