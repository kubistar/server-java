package kr.hhplus.be.server.service;


import kr.hhplus.be.server.ConcertNotFoundException;
import kr.hhplus.be.server.domain.Concert;
import kr.hhplus.be.server.dto.ConcertResponseDto;
import kr.hhplus.be.server.repository.ConcertRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ConcertService {

    private final ConcertRepository concertRepository;

    public ConcertService(ConcertRepository concertRepository) {
        this.concertRepository = concertRepository;
    }

    /**
     * 예약 가능한 콘서트 목록 조회
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @return 콘서트 목록과 페이징 정보
     */
    public Page<ConcertResponseDto> getAvailableConcerts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Concert> concerts = concertRepository.findAvailableConcerts(pageable);
        return concerts.map(ConcertResponseDto::from);
    }

    /**
     * 콘서트 상세 조회
     * @param concertId 콘서트 ID
     * @return 콘서트 상세 정보
     * @throws ConcertNotFoundException 콘서트가 존재하지 않는 경우
     */
    public ConcertResponseDto getConcertById(Long concertId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new ConcertNotFoundException("콘서트를 찾을 수 없습니다: " + concertId));
        return ConcertResponseDto.from(concert);
    }

    /**
     * 특정 날짜의 콘서트 목록 조회
     * @param date 조회할 날짜
     * @return 해당 날짜의 콘서트 목록
     */
    public List<ConcertResponseDto> getConcertsByDate(LocalDate date) {
        List<Concert> concerts = concertRepository.findByConcertDate(date);
        return concerts.stream()
                .map(ConcertResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 아티스트명으로 콘서트 검색
     * @param artistName 아티스트명 (부분 검색 가능)
     * @return 검색된 콘서트 목록
     */
    public List<ConcertResponseDto> searchConcertsByArtist(String artistName) {
        List<Concert> concerts = concertRepository.findByArtistContaining(artistName);
        return concerts.stream()
                .map(ConcertResponseDto::from)
                .collect(Collectors.toList());
    }
}