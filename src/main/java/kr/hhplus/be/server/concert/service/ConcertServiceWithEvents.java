package kr.hhplus.be.server.concert.service;

import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.event.ConcertSoldOutEvent;
import kr.hhplus.be.server.concert.repository.ConcertRepository;
import kr.hhplus.be.server.common.event.EventPublisher;
import kr.hhplus.be.server.common.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ConcertServiceWithEvents extends ConcertService {

    private final ConcertRepository concertRepository;
    private final EventPublisher eventPublisher;

    /**
     * 콘서트 매진 처리 (이벤트 발행 포함)
     */
    @Transactional
    public void markConcertAsSoldOut(Long concertId) {
        log.info("콘서트 매진 처리: concertId={}", concertId);

        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new IllegalArgumentException("콘서트를 찾을 수 없습니다: " + concertId));

        // 매진 처리
        concert.markAsSoldOut();
        concertRepository.save(concert);

        // ✨ 매진 이벤트 발행
        ConcertSoldOutEvent event = ConcertSoldOutEvent.createWithDuration(
                concert.getId(),
                concert.getName(),
                concert.getBookingStartTime(),
                concert.getSoldOutTime(),
                concert.getTotalSeats()
        );

        eventPublisher.publishEvent(KafkaTopics.CONCERT_EVENTS, concertId.toString(), event);

        log.info("콘서트 매진 처리 완료 및 이벤트 발행: concertId={}, duration={}분",
                concertId, event.getSoldOutDurationMinutes());
    }
}