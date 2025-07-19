package kr.hhplus.be.server.ranking;

import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.repository.ConcertRepository;
import kr.hhplus.be.server.ranking.service.StatisticsBatchScheduler;
import kr.hhplus.be.server.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StatisticsBatchScheduler 테스트")
class StatisticsBatchSchedulerTest {

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @InjectMocks
    private StatisticsBatchScheduler statisticsBatchScheduler;

    private Concert testConcert1;
    private Concert testConcert2;
    private Concert soldOutConcert;

    @BeforeEach
    void setUp() {
        // Mock Redis Operations 설정
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        // 테스트용 콘서트 데이터 생성
        testConcert1 = new Concert(
                "블랙핑크 월드투어",
                "BLACKPINK",
                "고양",
                LocalDate.now().plusDays(30),
                LocalTime.of(19, 0),
                100
        );

        testConcert2 = new Concert(
                "BTS 콘서트",
                "BTS",
                "잠실주경기장",
                LocalDate.now().plusDays(45),
                LocalTime.of(20, 0),
                80
        );

        soldOutConcert = new Concert(
                "뉴진스 콘서트",
                "NewJeans",
                "코엑스",
                LocalDate.now().plusDays(60),
                LocalTime.of(18, 0),
                50
        );
        soldOutConcert.markAsSoldOut();
    }

    @Test
    @DisplayName("주요 통계 배치 - 정상 동작 테스트")
    void updateMainStatistics_Success() {
        // Given
        List<Concert> concerts = Arrays.asList(testConcert1, testConcert2, soldOutConcert);
        when(concertRepository.findAll()).thenReturn(concerts);

        // 예약 수 Mock 데이터
        when(reservationRepository.countByConcertIdAndStatus(any(), eq("CONFIRMED")))
                .thenReturn(50L, 60L, 50L);

        when(reservationRepository.getReservationCountByConcer())
                .thenReturn(Arrays.asList(
                        new Object[]{1L, 50L},
                        new Object[]{2L, 60L},
                        new Object[]{3L, 50L}
                ));

        // ZSet Mock 설정
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.reverseRank(anyString(), any())).thenReturn(null);

        // When
        statisticsBatchScheduler.updateMainStatistics();

        // Then
        // 최소한 last update는 저장되어야 함
        verify(valueOperations).set(eq("statistics:last:update"), any(String.class));

        // Repository 호출 확인
        verify(concertRepository, atLeast(1)).findAll();
    }

    @Test
    @DisplayName("예외 발생 시 로그 기록 및 계속 진행")
    void updateMainStatistics_ExceptionHandling() {
        // Given
        when(concertRepository.findAll()).thenThrow(new RuntimeException("DB 연결 오류"));

        // When & Then
        // 예외가 발생해도 메서드가 정상 종료되어야 함
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            statisticsBatchScheduler.updateMainStatistics();
        });

        // 마지막 업데이트 시간은 저장되지 않아야 함
        verify(valueOperations, never()).set(eq("statistics:last:update"), any(String.class));
    }

    @Test
    @DisplayName("빈 콘서트 목록 처리")
    void updateMainStatistics_EmptyConcertList() {
        // Given
        when(concertRepository.findAll()).thenReturn(Arrays.asList());
        when(reservationRepository.getReservationCountByConcer()).thenReturn(Arrays.asList());

        // ZSet Mock 설정
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        // When
        statisticsBatchScheduler.updateMainStatistics();

        // Then
        // 마지막 업데이트 시간은 저장되어야 함
        verify(valueOperations).set(eq("statistics:last:update"), any(String.class));
    }

    @Test
    @DisplayName("상세 통계 배치 - 정상 동작 테스트")
    void updateDetailedStatistics_Success() {
        // When
        statisticsBatchScheduler.updateDetailedStatistics();

        // Then
        // 상세 통계 관련 Redis 호출이 있었는지만 확인
        verify(valueOperations, atLeastOnce()).set(
                startsWith("statistics:"),
                any(),
                any(Long.class),
                any()
        );
    }

    @Test
    @DisplayName("배치 스케줄러 기본 동작 확인")
    void basicBatchOperation() {
        // Given
        when(concertRepository.findAll()).thenReturn(Arrays.asList());
        when(reservationRepository.getReservationCountByConcer()).thenReturn(Arrays.asList());

        // ZSet operations Mock
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        // When
        statisticsBatchScheduler.updateMainStatistics();

        // Then
        // 실제로는 여러 메서드에서 findAll()을 호출하므로 atLeast(1) 사용
        verify(concertRepository, atLeast(1)).findAll();
        verify(reservationRepository, atLeast(1)).getReservationCountByConcer();
    }
}