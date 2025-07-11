package kr.hhplus.be.server.ranking.scheduler;

import kr.hhplus.be.server.ranking.service.ConcertRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RankingScheduler {

    private final ConcertRankingService rankingService;

    /**
     * 매 1분마다 실행 - 인기도 랭킹 재계산
     */
    @Scheduled(fixedRate = 60000) // 1분 = 60,000ms
    public void updatePopularityRanking() {
        try {
            log.debug("인기도 랭킹 업데이트 시작");

            // 현재 예약 중인 모든 콘서트에 대해 인기도 재계산
            // 실제 구현에서는 현재 활성 콘서트 목록을 조회하여 처리

            log.debug("인기도 랭킹 업데이트 완료");
        } catch (Exception e) {
            log.error("인기도 랭킹 업데이트 중 오류 발생", e);
        }
    }

    /**
     * 매 10분마다 실행 - 오래된 데이터 정리
     */
    @Scheduled(fixedRate = 600000) // 10분 = 600,000ms
    public void cleanupOldData() {
        try {
            log.debug("오래된 랭킹 데이터 정리 시작");

            // 여기서 오래된 예약 카운터 데이터 정리
            // Redis의 TTL을 활용하여 자동 정리되지만, 추가적인 정리 작업 수행

            log.debug("오래된 랭킹 데이터 정리 완료");
        } catch (Exception e) {
            log.error("데이터 정리 중 오류 발생", e);
        }
    }
}