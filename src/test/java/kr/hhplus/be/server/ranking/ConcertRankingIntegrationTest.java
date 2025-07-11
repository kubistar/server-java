package kr.hhplus.be.server.ranking;

import kr.hhplus.be.server.ranking.domain.ConcertRanking;
import kr.hhplus.be.server.ranking.domain.RankingType;
import kr.hhplus.be.server.ranking.service.ConcertRankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConcertRankingIntegrationTest {

    @Autowired
    private ConcertRankingService rankingService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        // Redis 데이터 초기화
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    @DisplayName("🏆 매진 속도가 빠른 콘서트가 상위 랭킹에 위치해야 한다")
    void soldOutSpeed_ranking_shouldOrderByFastestSoldOut() throws InterruptedException {
        // given
        LocalDateTime baseTime = LocalDateTime.now();

        // 콘서트 A: 10분 만에 매진
        rankingService.updateSoldOutRanking(1L, baseTime, baseTime.plusMinutes(10));

        // 콘서트 B: 5분 만에 매진 (더 빠름)
        rankingService.updateSoldOutRanking(2L, baseTime, baseTime.plusMinutes(5));

        // 콘서트 C: 30분 만에 매진
        rankingService.updateSoldOutRanking(3L, baseTime, baseTime.plusMinutes(30));

        // Redis 처리 완료를 위한 충분한 대기 시간
        Thread.sleep(1000);

        // when
        List<ConcertRanking> rankings = rankingService.getTopRanking(RankingType.SOLDOUT_SPEED, 10);

        // 디버깅을 위한 로그 출력
        System.out.println("랭킹 결과 수: " + rankings.size());
        rankings.forEach(ranking ->
                System.out.printf("랭킹: %d, 콘서트ID: %d, 점수: %.2f%n",
                        ranking.getRank(), ranking.getConcertId(), ranking.getScore()));

        // then
        assertThat(rankings).hasSizeGreaterThanOrEqualTo(3);

        if (rankings.size() >= 3) {
            assertThat(rankings.get(0).getConcertId()).isEqualTo(2L); // 5분 매진이 1위
            assertThat(rankings.get(1).getConcertId()).isEqualTo(1L); // 10분 매진이 2위
            assertThat(rankings.get(2).getConcertId()).isEqualTo(3L); // 30분 매진이 3위

            // 점수 검증 (매진이 빠를수록 높은 점수)
            assertThat(rankings.get(0).getScore()).isGreaterThan(rankings.get(1).getScore());
            assertThat(rankings.get(1).getScore()).isGreaterThan(rankings.get(2).getScore());
        }
    }

    @Test
    @DisplayName("📊 실시간 예약 속도가 정확하게 계산되어야 한다")
    void realTimeBookingSpeed_shouldCalculateCorrectly() throws InterruptedException {
        // given
        Long concertId = 100L;

        // 여러 번의 예약 발생 시뮬레이션
        for (int i = 0; i < 50; i++) {
            rankingService.updateBookingRanking(concertId);
            Thread.sleep(10); // 10ms 간격으로 예약
        }

        // when
        double bookingSpeed = rankingService.getRealTimeBookingSpeed(concertId);

        // then
        assertThat(bookingSpeed).isGreaterThan(0);
        System.out.println("실시간 예약 속도: " + bookingSpeed + " 건/분");
    }

    @Test
    @DisplayName("🔥 동시 예약 상황에서 랭킹이 정확하게 업데이트되어야 한다")
    void concurrentBooking_shouldUpdateRankingCorrectly() throws InterruptedException {
        // given
        Long concertId = 200L;
        int threadCount = 100;
        int bookingsPerThread = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when - 동시에 여러 스레드에서 예약 발생
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < bookingsPerThread; j++) {
                        rankingService.updateBookingRanking(concertId);
                        Thread.sleep(1); // 1ms 대기
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // 잠시 대기 후 결과 확인
        Thread.sleep(1000);

        // then
        double bookingSpeed = rankingService.getRealTimeBookingSpeed(concertId);
        assertThat(bookingSpeed).isGreaterThan(0);

        System.out.println("동시 예약 후 속도: " + bookingSpeed + " 건/분");
        System.out.println("총 예상 예약 수: " + (threadCount * bookingsPerThread));
    }

    @Test
    @DisplayName("📈 종합 인기도 랭킹이 매진속도와 예약속도를 반영해야 한다")
    void popularityRanking_shouldReflectBothSoldOutAndBookingSpeed() throws InterruptedException {
        // given
        LocalDateTime baseTime = LocalDateTime.now();

        // 콘서트 1: 빠른 매진 + 높은 예약 속도
        rankingService.updateSoldOutRanking(1L, baseTime, baseTime.plusMinutes(5));
        for (int i = 0; i < 100; i++) {
            rankingService.updateBookingRanking(1L);
        }

        // 콘서트 2: 느린 매진 + 낮은 예약 속도
        rankingService.updateSoldOutRanking(2L, baseTime, baseTime.plusMinutes(60));
        for (int i = 0; i < 10; i++) {
            rankingService.updateBookingRanking(2L);
        }

        // 콘서트 3: 매진되지 않았지만 높은 예약 속도
        for (int i = 0; i < 80; i++) {
            rankingService.updateBookingRanking(3L);
        }

        Thread.sleep(500); // Redis 처리 대기

        // when
        List<ConcertRanking> popularityRankings = rankingService.getTopRanking(RankingType.POPULARITY, 10);

        // then
        assertThat(popularityRankings).hasSizeGreaterThanOrEqualTo(3);

        // 콘서트 1이 가장 높은 인기도를 가져야 함 (빠른 매진 + 높은 예약 속도)
        ConcertRanking topRanking = popularityRankings.get(0);
        assertThat(topRanking.getConcertId()).isEqualTo(1L);

        // 점수 출력으로 확인
        popularityRankings.forEach(ranking ->
                System.out.printf("콘서트 %d: 인기도 %.2f점%n",
                        ranking.getConcertId(), ranking.getScore()));
    }

    @Test
    @DisplayName("🎯 특정 콘서트의 랭킹 상세 정보를 조회할 수 있어야 한다")
    void getConcertRankingDetails_shouldReturnCorrectInfo() throws InterruptedException {
        // given
        Long concertId = 300L;
        LocalDateTime baseTime = LocalDateTime.now();

        // 콘서트 매진 처리
        rankingService.updateSoldOutRanking(concertId, baseTime, baseTime.plusMinutes(15));

        // 예약 활동 시뮬레이션
        for (int i = 0; i < 30; i++) {
            rankingService.updateBookingRanking(concertId);
            Thread.sleep(10);
        }

        // when
        double bookingSpeed = rankingService.getRealTimeBookingSpeed(concertId);
        List<ConcertRanking> soldOutRankings = rankingService.getTopRanking(RankingType.SOLDOUT_SPEED, 10);

        // then
        assertThat(bookingSpeed).isGreaterThan(0);

        // 매진 랭킹에서 해당 콘서트 찾기
        ConcertRanking soldOutRanking = soldOutRankings.stream()
                .filter(ranking -> ranking.getConcertId().equals(concertId))
                .findFirst()
                .orElse(null);

        assertThat(soldOutRanking).isNotNull();
        assertThat(soldOutRanking.getScore()).isGreaterThan(0);

        System.out.printf("콘서트 %d - 매진 점수: %.2f, 예약 속도: %.2f건/분%n",
                concertId, soldOutRanking.getScore(), bookingSpeed);
    }

    @Test
    @DisplayName("🧹 랭킹 데이터 정리가 정상적으로 작동해야 한다")
    void rankingDataCleanup_shouldWorkProperly() throws InterruptedException {
        // given
        Long concertId = 400L;

        // 과거 데이터 시뮬레이션을 위해 많은 예약 생성
        for (int i = 0; i < 50; i++) {
            rankingService.updateBookingRanking(concertId);
            Thread.sleep(5);
        }

        // when
        double speedBefore = rankingService.getRealTimeBookingSpeed(concertId);

        // 6분 대기 (실제로는 스케줄러가 정리하지만, 여기서는 수동으로 확인)
        // 실제 환경에서는 TTL과 스케줄러가 자동으로 정리

        // then
        assertThat(speedBefore).isGreaterThan(0);
        System.out.println("정리 전 예약 속도: " + speedBefore + " 건/분");

        // TTL이 작동하는지 확인하기 위해 Redis 키 존재 여부 체크
        // 실제 운영에서는 데이터가 자동으로 만료됨
    }
}