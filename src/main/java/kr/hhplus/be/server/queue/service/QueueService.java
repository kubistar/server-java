package kr.hhplus.be.server.queue.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import kr.hhplus.be.server.queue.domain.QueueStatus;
import kr.hhplus.be.server.queue.domain.QueueToken;
import kr.hhplus.be.server.queue.exception.QueueTokenExpiredException;
import kr.hhplus.be.server.queue.exception.QueueTokenNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 대기열 관리 서비스
 * Redis를 활용한 분산 대기열 시스템
 */
@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${queue.max-active-users:100}")
    private int maxActiveUsers;

    @Value("${queue.token-expire-minutes:30}")
    private int tokenExpireMinutes;

    @Value("${queue.wait-time-per-user:10}")
    private int waitTimePerUser;

    @Value("${queue.lock-timeout-seconds:5}")
    private int lockTimeoutSeconds;

    // Redis 키 패턴
    private static final String QUEUE_TOKEN_KEY = "queue:token:";
    private static final String WAITING_QUEUE_KEY = "queue:waiting";
    private static final String ACTIVE_USERS_KEY = "queue:active";
    private static final String USER_TOKEN_MAPPING_KEY = "queue:user:token:";
    private static final String USER_ACTIVE_KEY_PREFIX = "queue:user:active:";
    private static final String QUEUE_LOCK_KEY = "queue:lock";

    public QueueService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;

        // ObjectMapper 설정
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
    }

    /**
     * 대기열 토큰 발급 (피드백 반영 - 동시성 문제 해결)
     * @param userId 사용자 ID
     * @return 발급된 토큰 정보
     */
    public QueueToken issueToken(String userId) {
        log.info("대기열 토큰 발급 요청: userId={}", userId);

        // 기존 토큰 확인
        QueueToken existingToken = findExistingToken(userId);
        if (existingToken != null && !existingToken.isExpired()) {
            log.info("기존 토큰 반환: userId={}, token={}", userId, existingToken.getToken());
            return existingToken;
        }

        // 분산 락 획득 시도 (동시성 문제 해결)
        String lockValue = UUID.randomUUID().toString();
        boolean lockAcquired = acquireLock(QUEUE_LOCK_KEY, lockValue, lockTimeoutSeconds);

        if (!lockAcquired) {
            throw new RuntimeException("대기열 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            return issueTokenWithLock(userId);
        } finally {
            releaseLock(QUEUE_LOCK_KEY, lockValue);
        }
    }


    /**
     * 분산 락이 획득된 상태에서 대기열 토큰을 발급
     * 활성 사용자 수를 확인하여 즉시 활성화하거나 대기열에 추가
     *
     * @param userId 토큰을 발급받을 사용자 ID
     * @return 발급된 대기열 토큰 정보 (QueueToken)
     */
    private QueueToken issueTokenWithLock(String userId) {
        // 새 토큰 생성
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(tokenExpireMinutes);

        // 만료된 사용자들 먼저 정리
        cleanupExpiredUsers();

        // 현재 활성 사용자 수 확인 (정리 후)
        Long activeUserCount = redisTemplate.opsForSet().size(ACTIVE_USERS_KEY);
        log.info("현재 활성 사용자 수: {}, 최대 허용: {}", activeUserCount, maxActiveUsers);

        QueueToken queueToken;

        if (activeUserCount != null && activeUserCount < maxActiveUsers) {
            // 즉시 활성화
            queueToken = new QueueToken(token, userId, 0L, 0,
                    QueueStatus.ACTIVE, now, expiresAt);

            // 활성 사용자 목록에 추가 (개별 expire 관리로 수정)
            addActiveUserWithIndividualExpire(userId, token, expiresAt);

            log.info("즉시 활성화: userId={}, token={}", userId, token);
        } else {
            // 대기열에 추가
            Double score = (double) System.currentTimeMillis();
            redisTemplate.opsForZSet().add(WAITING_QUEUE_KEY, userId, score);

            // 대기 순서 계산
            Long position = redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, userId);
            position = position != null ? position + 1 : 1; // rank는 0부터 시작

            Integer estimatedWaitTime = (int) (position * waitTimePerUser / 60); // 분 단위

            queueToken = new QueueToken(token, userId, position, estimatedWaitTime,
                    QueueStatus.WAITING, now, expiresAt);

            log.info("대기열 추가: userId={}, position={}, waitTime={}분", userId, position, estimatedWaitTime);
        }

        // 토큰 정보 Redis에 저장
        redisTemplate.opsForValue().set(QUEUE_TOKEN_KEY + token, queueToken,
                tokenExpireMinutes, TimeUnit.MINUTES);

        // 사용자-토큰 매핑 저장 (기존 토큰 찾기용)
        redisTemplate.opsForValue().set(USER_TOKEN_MAPPING_KEY + userId, token,
                tokenExpireMinutes, TimeUnit.MINUTES);

        log.info("토큰 발급 완료: userId={}, token={}, status={}", userId, token, queueToken.getStatus());
        return queueToken;
    }

    /**
     * 활성 사용자를 개별 만료 시간과 함께 추가
     * Redis Set에 사용자를 추가하고, 개별 사용자별 활성 상태 키로 만료시간을 관리
     *
     * @param userId 추가할 사용자의 고유 ID
     * @param token 발급된 대기열 토큰
     * @param expiresAt 토큰이 만료되는 시간
     */
    private void addActiveUserWithIndividualExpire(String userId, String token, LocalDateTime expiresAt) {
        // Set에 사용자 추가 (공통 Set은 expire 설정하지 않음)
        redisTemplate.opsForSet().add(ACTIVE_USERS_KEY, userId);

        // 개별 사용자별 활성 상태 키로 만료시간 관리
        String userActiveKey = USER_ACTIVE_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(userActiveKey, token, tokenExpireMinutes, TimeUnit.MINUTES);

        log.info("사용자 {}가 활성 상태로 등록되었습니다. (개별 expire 적용)", userId);
    }

    /**
     * 만료된 사용자들 정리
     */
    private void cleanupExpiredUsers() {
        Set<Object> activeUsers = redisTemplate.opsForSet().members(ACTIVE_USERS_KEY);
        if (activeUsers != null) {
            for (Object userIdObj : activeUsers) {
                String userId = (String) userIdObj;
                String userActiveKey = USER_ACTIVE_KEY_PREFIX + userId;

                if (!Boolean.TRUE.equals(redisTemplate.hasKey(userActiveKey))) {
                    // 개별 활성 키가 만료되었으면 Set에서도 제거
                    redisTemplate.opsForSet().remove(ACTIVE_USERS_KEY, userId);
                    log.info("만료된 사용자 {} 정리 완료", userId);
                }
            }
        }
    }

    /**
     */
    private QueueToken findExistingToken(String userId) {
        try {
            String userTokenKey = USER_TOKEN_MAPPING_KEY + userId;
            String existingToken = (String) redisTemplate.opsForValue().get(userTokenKey);

            if (existingToken != null) {
                return getTokenFromRedis(QUEUE_TOKEN_KEY + existingToken);
            }

            return null;
        } catch (Exception e) {
            log.error("기존 토큰 조회 실패: userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 대기열 상태 조회 (수정됨)
     */
    public QueueToken getQueueStatus(String token) {
        log.info("대기열 상태 조회: token={}", token);

        QueueToken queueToken = getTokenFromRedis(QUEUE_TOKEN_KEY + token);

        if (queueToken == null) {
            log.warn("토큰을 찾을 수 없음: token={}", token);
            throw new QueueTokenNotFoundException("유효하지 않은 토큰입니다: " + token);
        }

        if (queueToken.isExpired()) {
            log.info("만료된 토큰: token={}", token);
            expireToken(token, queueToken.getUserId());
            throw new QueueTokenExpiredException("만료된 토큰입니다: " + token);
        }

        // 대기 중인 경우 순서 업데이트
        if (queueToken.getStatus() == QueueStatus.WAITING) {
            updateWaitingPosition(queueToken);
        }

        log.info("대기열 상태 조회 완료: token={}, status={}, position={}",
                token, queueToken.getStatus(), queueToken.getQueuePosition());

        return queueToken;
    }

    /**
     * 토큰 유효성 검증 (좌석 조회 시 사용)
     */
    public boolean validateActiveToken(String token) {
        log.info("토큰 유효성 검증: token={}", token);

        if (token == null || token.trim().isEmpty()) {
            log.warn("토큰이 없음");
            return false;
        }

        try {
            QueueToken queueToken = getQueueStatus(token);
            boolean isValid = queueToken.isActive();
            log.info("토큰 검증 결과: token={}, valid={}, status={}", token, isValid, queueToken.getStatus());
            return isValid;
        } catch (Exception e) {
            log.warn("토큰 검증 실패: token={}, error={}", token, e.getMessage());
            return false;
        }
    }

    /**
     * 대기열에서 사용자를 활성화
     */
    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    public void activateWaitingUsers() {
        log.info("대기 중인 사용자 활성화 프로세스 시작");

        // 분산 락 획득 시도
        String lockValue = UUID.randomUUID().toString();
        boolean lockAcquired = acquireLock(QUEUE_LOCK_KEY, lockValue, lockTimeoutSeconds);

        if (!lockAcquired) {
            log.debug("다른 프로세스에서 활성화 작업 진행 중");
            return;
        }

        try {
            activateWaitingUsersWithLock();
        } finally {
            releaseLock(QUEUE_LOCK_KEY, lockValue);
        }
    }

    private void activateWaitingUsersWithLock() {
        // 만료된 사용자들 먼저 정리
        cleanupExpiredUsers();

        Long activeUserCount = redisTemplate.opsForSet().size(ACTIVE_USERS_KEY);
        long availableSlots = maxActiveUsers - (activeUserCount != null ? activeUserCount : 0);

        if (availableSlots <= 0) {
            log.info("활성화 가능한 슬롯 없음: activeUsers={}, maxActive={}", activeUserCount, maxActiveUsers);
            return;
        }

        // 대기열에서 가장 오래 기다린 사용자들 가져오기
        Set<Object> waitingUsers = redisTemplate.opsForZSet().range(WAITING_QUEUE_KEY, 0, availableSlots - 1);

        if (waitingUsers == null || waitingUsers.isEmpty()) {
            log.info("대기 중인 사용자 없음");
            return;
        }

        for (Object userIdObj : waitingUsers) {
            String userId = (String) userIdObj;

            // 대기열에서 제거
            redisTemplate.opsForZSet().remove(WAITING_QUEUE_KEY, userId);

            // 활성 사용자로 추가 (개별 expire 관리)
            String userToken = (String) redisTemplate.opsForValue().get(USER_TOKEN_MAPPING_KEY + userId);
            if (userToken != null) {
                LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(tokenExpireMinutes);
                addActiveUserWithIndividualExpire(userId, userToken, expiresAt);

                // 해당 사용자의 토큰 활성화
                activateUserToken(userId, userToken);
            }

            log.info("사용자 활성화 완료: userId={}", userId);
        }

        log.info("대기 중인 사용자 활성화 완료: activatedCount={}", waitingUsers.size());
    }

    /**
     * 대기 순서 업데이트
     */
    private void updateWaitingPosition(QueueToken queueToken) {
        Long position = redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, queueToken.getUserId());
        if (position != null) {
            position = position + 1; // rank는 0부터 시작
            Integer estimatedWaitTime = (int) (position * waitTimePerUser / 60);
            queueToken.updatePosition(position, estimatedWaitTime);

            // Redis에 업데이트된 정보 저장
            redisTemplate.opsForValue().set(QUEUE_TOKEN_KEY + queueToken.getToken(), queueToken,
                    tokenExpireMinutes, TimeUnit.MINUTES);
        }
    }

    /**
     * 사용자 토큰 활성화
     */
    private void activateUserToken(String userId, String token) {
        QueueToken queueToken = (QueueToken) redisTemplate.opsForValue().get(QUEUE_TOKEN_KEY + token);
        if (queueToken != null) {
            queueToken.activate();
            queueToken.updatePosition(0L, 0);

            // Redis에 업데이트된 토큰 저장
            redisTemplate.opsForValue().set(QUEUE_TOKEN_KEY + token, queueToken,
                    tokenExpireMinutes, TimeUnit.MINUTES);

            log.info("사용자 토큰 활성화 완료: userId={}, token={}", userId, token);
        }
    }

    /**
     * 토큰 만료 처리
     */
    private void expireToken(String token, String userId) {
        // 토큰 삭제
        redisTemplate.delete(QUEUE_TOKEN_KEY + token);

        // 사용자-토큰 매핑 삭제
        redisTemplate.delete(USER_TOKEN_MAPPING_KEY + userId);

        // 활성 사용자에서 제거
        redisTemplate.opsForSet().remove(ACTIVE_USERS_KEY, userId);
        redisTemplate.delete(USER_ACTIVE_KEY_PREFIX + userId);

        log.info("토큰 만료 처리 완료: token={}, userId={}", token, userId);
    }

    /**
     * 분산 락 획득
     */
    private boolean acquireLock(String lockKey, String lockValue, int timeoutSeconds) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockValue,
                timeoutSeconds,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(result);
    }

    /**
     * 분산 락 해제
     */
    private void releaseLock(String lockKey, String lockValue) {
        String script =
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "    return redis.call('del', KEYS[1]) " +
                        "else " +
                        "    return 0 " +
                        "end";

        redisTemplate.execute(
                RedisScript.of(script, Long.class),
                Collections.singletonList(lockKey),
                lockValue
        );
    }

    /**
     * Redis에서 토큰 조회
     */
    private QueueToken getTokenFromRedis(String key) {
        try {
            Object obj = redisTemplate.opsForValue().get(key);

            if (obj == null) {
                return null;
            }

            // 이미 QueueToken 객체인 경우
            if (obj instanceof QueueToken) {
                return (QueueToken) obj;
            }

            // String (JSON)인 경우
            if (obj instanceof String) {
                return objectMapper.readValue((String) obj, QueueToken.class);
            }

            // LinkedHashMap인 경우
            if (obj instanceof LinkedHashMap) {
                String json = objectMapper.writeValueAsString(obj);
                return objectMapper.readValue(json, QueueToken.class);
            }

            log.warn("지원하지 않는 타입: {}", obj.getClass());
            return null;

        } catch (Exception e) {
            log.error("Redis에서 토큰 조회 실패: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Redis에 토큰 저장
     */
    private void saveTokenToRedis(String key, QueueToken queueToken) {
        try {
            // JSON 문자열로 저장하여 일관성 유지
            String json = objectMapper.writeValueAsString(queueToken);
            redisTemplate.opsForValue().set(key, json, tokenExpireMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis에 토큰 저장 실패: key={}, error={}", key, e.getMessage());
            throw new RuntimeException("토큰 저장 실패", e);
        }
    }
}