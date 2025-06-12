package kr.hhplus.be.server.queue;

import kr.hhplus.be.server.queue.domain.QueueStatus;
import kr.hhplus.be.server.queue.domain.QueueToken;
import kr.hhplus.be.server.queue.exception.QueueTokenNotFoundException;
import kr.hhplus.be.server.queue.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueueServiceTest {

    private static final Logger log = LoggerFactory.getLogger(QueueServiceTest.class);

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        log.info("=== 테스트 준비: QueueService 초기화 ===");

        // RedisTemplate의 Operations Mock 설정
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        queueService = new QueueService(redisTemplate);

        // 설정값 주입
        ReflectionTestUtils.setField(queueService, "maxActiveUsers", 100);
        ReflectionTestUtils.setField(queueService, "tokenExpireMinutes", 30);
        ReflectionTestUtils.setField(queueService, "waitTimePerUser", 10);

        log.info("QueueService 초기화 완료");
    }

    @Test
    @DisplayName("활성 사용자가 최대치 미만일 때 즉시 활성화된 토큰을 발급한다")
    void issueToken_WhenActiveUsersLessThanMax_ShouldIssueActiveToken() {
        // given
        log.info("=== 테스트 시작: 즉시 활성화 토큰 발급 ===");

        String userId = "user-123";
        log.info("테스트 사용자 ID: {}", userId);

        // 🔥 분산 락 획득 성공 Mock 추가
        when(valueOperations.setIfAbsent(eq("queue:lock"), anyString(), eq(5L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        // 🔥 기존 토큰 없음 Mock 추가
        when(valueOperations.get(startsWith("queue:user:token:"))).thenReturn(null);

        // 🔥 활성 사용자 목록이 비어있음 (만료된 사용자 정리용)
        when(setOperations.members("queue:active")).thenReturn(Collections.emptySet());

        // 현재 활성 사용자 수가 최대치 미만
        when(setOperations.size("queue:active")).thenReturn(50L);
        log.info("Mock 설정: 현재 활성 사용자 수 = 50 (최대 100 미만)");

        // when
        log.info("토큰 발급 요청: queueService.issueToken({})", userId);
        QueueToken result = queueService.issueToken(userId);

        // then
        log.info("=== 검증 시작 ===");
        log.info("발급된 토큰 정보:");
        log.info("  - Token: {}", result.getToken());
        log.info("  - UserId: {}", result.getUserId());
        log.info("  - Status: {}", result.getStatus());
        log.info("  - Position: {}", result.getQueuePosition());
        log.info("  - WaitTime: {}", result.getEstimatedWaitTimeMinutes());

        assertThat(result.getUserId()).isEqualTo(userId);
        log.info("✓ 사용자 ID 검증 통과: {}", result.getUserId());

        assertThat(result.getStatus()).isEqualTo(QueueStatus.ACTIVE);
        log.info("✓ 토큰 상태 검증 통과: {}", result.getStatus());

        assertThat(result.getQueuePosition()).isEqualTo(0L);
        log.info("✓ 대기 순서 검증 통과: {}", result.getQueuePosition());

        assertThat(result.getEstimatedWaitTimeMinutes()).isEqualTo(0);
        log.info("✓ 예상 대기 시간 검증 통과: {}분", result.getEstimatedWaitTimeMinutes());

        assertThat(result.getToken()).isNotNull();
        log.info("✓ 토큰 생성 검증 통과");

        // Redis 호출 검증
        verify(setOperations).add(eq("queue:active"), eq(userId));
        verify(valueOperations, times(3)).set(anyString(), any(), eq(30L), eq(TimeUnit.MINUTES));
        // 토큰 저장 + 사용자-토큰 매핑 + 개별 활성 키 = 3개
        log.info("✓ Redis 호출 검증 통과");

        log.info("=== 테스트 완료: 즉시 활성화 토큰 발급 검증 통과 ===");
    }

    @Test
    @DisplayName("활성 사용자가 최대치일 때 대기열에 추가된 토큰을 발급한다")
    void issueToken_WhenActiveUsersAtMax_ShouldIssueWaitingToken() {
        // given
        log.info("=== 테스트 시작: 대기열 토큰 발급 ===");

        String userId = "user-456";
        log.info("테스트 사용자 ID: {}", userId);

        // 🔥 분산 락 획득 성공 Mock 추가
        when(valueOperations.setIfAbsent(eq("queue:lock"), anyString(), eq(5L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        // 🔥 기존 토큰 없음 Mock 추가
        when(valueOperations.get(startsWith("queue:user:token:"))).thenReturn(null);

        // 🔥 활성 사용자 목록이 비어있음 (만료된 사용자 정리용)
        when(setOperations.members("queue:active")).thenReturn(Collections.emptySet());

        // 현재 활성 사용자 수가 최대치
        when(setOperations.size("queue:active")).thenReturn(100L);
        when(zSetOperations.rank("queue:waiting", userId)).thenReturn(9L); // 10번째 (0부터 시작)
        log.info("Mock 설정: 현재 활성 사용자 수 = 100 (최대치), 대기 순서 = 10번째");

        // when
        log.info("토큰 발급 요청: queueService.issueToken({})", userId);
        QueueToken result = queueService.issueToken(userId);

        // then
        log.info("=== 검증 시작 ===");
        log.info("발급된 토큰 정보:");
        log.info("  - Token: {}", result.getToken());
        log.info("  - UserId: {}", result.getUserId());
        log.info("  - Status: {}", result.getStatus());
        log.info("  - Position: {}", result.getQueuePosition());
        log.info("  - WaitTime: {}", result.getEstimatedWaitTimeMinutes());

        assertThat(result.getUserId()).isEqualTo(userId);
        log.info("✓ 사용자 ID 검증 통과: {}", result.getUserId());

        assertThat(result.getStatus()).isEqualTo(QueueStatus.WAITING);
        log.info("✓ 토큰 상태 검증 통과: {}", result.getStatus());

        assertThat(result.getQueuePosition()).isEqualTo(10L);
        log.info("✓ 대기 순서 검증 통과: {}번째", result.getQueuePosition());

        assertThat(result.getEstimatedWaitTimeMinutes()).isEqualTo(1); // 10 * 10 / 60 = 1분
        log.info("✓ 예상 대기 시간 검증 통과: {}분", result.getEstimatedWaitTimeMinutes());

        // Redis 호출 검증
        verify(zSetOperations).add(eq("queue:waiting"), eq(userId), anyDouble());
        verify(valueOperations, times(2)).set(anyString(), any(), eq(30L), eq(TimeUnit.MINUTES));
        // 토큰 저장 + 사용자-토큰 매핑 = 2개
        log.info("✓ Redis 호출 검증 통과");

        log.info("=== 테스트 완료: 대기열 토큰 발급 검증 통과 ===");
    }

    @Test
    @DisplayName("유효한 토큰으로 대기열 상태를 조회한다")
    void getQueueStatus_ValidToken_ShouldReturnTokenInfo() {
        // given
        String token = "valid-token-123";
        String userId = "user-123";

        QueueToken mockToken = new QueueToken(
                token,
                userId,
                5L,
                2,
                QueueStatus.WAITING,
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now().plusMinutes(30)
        );

        when(valueOperations.get("queue:token:" + token)).thenReturn(mockToken);

        // 🔥 zSetOperations Mock 추가 - 5번째 순서를 위해 rank는 4L
        when(zSetOperations.rank("queue:waiting", userId)).thenReturn(4L);

        // when
        QueueToken result = queueService.getQueueStatus(token);

        // then
        assertThat(result.getToken()).isEqualTo(token);
        assertThat(result.getStatus()).isEqualTo(QueueStatus.WAITING);
        assertThat(result.getQueuePosition()).isEqualTo(5L); // 4L + 1 = 5L

        verify(valueOperations).get("queue:token:" + token);
        verify(zSetOperations).rank("queue:waiting", userId);
    }

    @Test
    @DisplayName("존재하지 않는 토큰 조회 시 예외를 발생시킨다")
    void getQueueStatus_NonExistingToken_ShouldThrowException() {
        // given
        log.info("=== 테스트 시작: 존재하지 않는 토큰 조회 ===");

        String nonExistingToken = "non-existing-token";
        log.info("존재하지 않는 토큰: {}", nonExistingToken);

        when(valueOperations.get("queue:token:" + nonExistingToken)).thenReturn(null);

        // when & then
        log.info("예외 발생 검증: queueService.getQueueStatus({})", nonExistingToken);

        assertThatThrownBy(() -> queueService.getQueueStatus(nonExistingToken))
                .isInstanceOf(QueueTokenNotFoundException.class)
                .hasMessage("유효하지 않은 토큰입니다: " + nonExistingToken);

        log.info("✓ QueueTokenNotFoundException 발생 검증 통과");

        verify(valueOperations).get("queue:token:" + nonExistingToken);
        log.info("✓ Redis 호출 검증 통과");

        log.info("=== 테스트 완료: 존재하지 않는 토큰 예외 처리 검증 통과 ===");
    }

    @Test
    @DisplayName("유효한 활성 토큰 검증이 성공한다")
    void validateActiveToken_ValidActiveToken_ShouldReturnTrue() {
        // given
        log.info("=== 테스트 시작: 유효한 활성 토큰 검증 ===");

        String activeToken = "active-token-123";
        QueueToken mockActiveToken = new QueueToken(
                activeToken,
                "user-123",
                0L,
                0,
                QueueStatus.ACTIVE,
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now().plusMinutes(30)
        );
        log.info("Mock 활성 토큰 정보: token={}, status={}", activeToken, mockActiveToken.getStatus());

        when(valueOperations.get("queue:token:" + activeToken)).thenReturn(mockActiveToken);

        // when
        log.info("토큰 검증: queueService.validateActiveToken({})", activeToken);
        boolean result = queueService.validateActiveToken(activeToken);

        // then
        log.info("토큰 검증 결과: {}", result);

        assertThat(result).isTrue();
        log.info("✓ 유효한 활성 토큰 검증 통과");

        verify(valueOperations).get("queue:token:" + activeToken);
        log.info("✓ Redis 호출 검증 통과");

        log.info("=== 테스트 완료: 유효한 활성 토큰 검증 성공 ===");
    }

    @Test
    @DisplayName("대기 중인 토큰 검증이 실패한다")
    void validateActiveToken_WaitingToken_ShouldReturnFalse() {
        // given
        log.info("=== 테스트 시작: 대기 중인 토큰 검증 ===");

        String waitingToken = "waiting-token-123";
        QueueToken mockWaitingToken = new QueueToken(
                waitingToken,
                "user-456",
                10L,
                3,
                QueueStatus.WAITING,
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now().plusMinutes(30)
        );
        log.info("Mock 대기 토큰 정보: token={}, status={}, position={}",
                waitingToken, mockWaitingToken.getStatus(), mockWaitingToken.getQueuePosition());

        when(valueOperations.get("queue:token:" + waitingToken)).thenReturn(mockWaitingToken);

        // when
        log.info("토큰 검증: queueService.validateActiveToken({})", waitingToken);
        boolean result = queueService.validateActiveToken(waitingToken);

        // then
        log.info("토큰 검증 결과: {}", result);

        assertThat(result).isFalse();
        log.info("✓ 대기 중인 토큰 검증 실패 확인");

        log.info("=== 테스트 완료: 대기 중인 토큰 검증 실패 처리 성공 ===");
    }
}