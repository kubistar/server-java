package kr.hhplus.be.server.queue.service;

import kr.hhplus.be.server.queue.domain.QueueStatus;
import kr.hhplus.be.server.queue.domain.QueueToken;
import kr.hhplus.be.server.queue.exception.QueueTokenExpiredException;
import kr.hhplus.be.server.queue.exception.QueueTokenNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 대기열 관리 서비스
 * Redis를 활용한 분산 대기열 시스템
 */
@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${queue.max-active-users:100}")
    private int maxActiveUsers;

    @Value("${queue.token-expire-minutes:30}")
    private int tokenExpireMinutes;

    @Value("${queue.wait-time-per-user:10}")
    private int waitTimePerUser;

    // Redis 키 패턴
    private static final String QUEUE_TOKEN_KEY = "queue:token:";
    private static final String WAITING_QUEUE_KEY = "queue:waiting";
    private static final String ACTIVE_USERS_KEY = "queue:active";

    public QueueService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 대기열 토큰 발급
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

        // 새 토큰 생성
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(tokenExpireMinutes);

        // 현재 활성 사용자 수 확인
        Long activeUserCount = redisTemplate.opsForSet().size(ACTIVE_USERS_KEY);
        log.info("현재 활성 사용자 수: {}, 최대 허용: {}", activeUserCount, maxActiveUsers);

        QueueToken queueToken;

        if (activeUserCount < maxActiveUsers) {
            // 즉시 활성화
            queueToken = new QueueToken(token, userId, 0L, 0,
                    QueueStatus.ACTIVE, now, expiresAt);

            // 활성 사용자 목록에 추가
            redisTemplate.opsForSet().add(ACTIVE_USERS_KEY, userId);
            redisTemplate.expire(ACTIVE_USERS_KEY, tokenExpireMinutes, TimeUnit.MINUTES);

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

        log.info("토큰 발급 완료: userId={}, token={}, status={}", userId, token, queueToken.getStatus());
        return queueToken;
    }

    /**
     * 대기열 상태 조회
     * @param token 대기열 토큰
     * @return 현재 대기열 상태
     */
    public QueueToken getQueueStatus(String token) {
        log.info("대기열 상태 조회: token={}", token);

        QueueToken queueToken = (QueueToken) redisTemplate.opsForValue().get(QUEUE_TOKEN_KEY + token);

        if (queueToken == null) {
            log.warn("토큰을 찾을 수 없음: token={}", token);
            throw new QueueTokenNotFoundException("유효하지 않은 토큰입니다: " + token);
        }

        if (queueToken.isExpired()) {
            log.info("만료된 토큰: token={}", token);
            expireToken(token);
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
     * @param token 대기열 토큰
     * @return 유효한 활성 토큰인지 여부
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
     * 대기열에서 사용자를 활성화 (스케줄러에서 호출)
     */
    public void activateWaitingUsers() {
        log.info("대기 중인 사용자 활성화 프로세스 시작");

        Long activeUserCount = redisTemplate.opsForSet().size(ACTIVE_USERS_KEY);
        long availableSlots = maxActiveUsers - (activeUserCount != null ? activeUserCount : 0);

        if (availableSlots <= 0) {
            log.info("활성화 가능한 슬롯 없음: activeUsers={}, maxActive={}", activeUserCount, maxActiveUsers);
            return;
        }

        // 대기열에서 가장 오래 기다린 사용자들 가져오기
        var waitingUsers = redisTemplate.opsForZSet().range(WAITING_QUEUE_KEY, 0, availableSlots - 1);

        if (waitingUsers == null || waitingUsers.isEmpty()) {
            log.info("대기 중인 사용자 없음");
            return;
        }

        for (Object userIdObj : waitingUsers) {
            String userId = (String) userIdObj;

            // 대기열에서 제거
            redisTemplate.opsForZSet().remove(WAITING_QUEUE_KEY, userId);

            // 활성 사용자로 추가
            redisTemplate.opsForSet().add(ACTIVE_USERS_KEY, userId);

            // 해당 사용자의 토큰 찾아서 활성화
            activateUserToken(userId);

            log.info("사용자 활성화 완료: userId={}", userId);
        }

        log.info("대기 중인 사용자 활성화 완료: activatedCount={}", waitingUsers.size());
    }

    /**
     * 기존 토큰 찾기
     */
    private QueueToken findExistingToken(String userId) {
        // TODO: userId로 기존 토큰을 찾는 로직 구현
        // 현재는 간단히 null 반환
        return null;
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
    private void activateUserToken(String userId) {
        // TODO: userId로 토큰을 찾아서 활성화하는 로직 구현
        log.debug("사용자 토큰 활성화: userId={}", userId);
    }

    /**
     * 토큰 만료 처리
     */
    private void expireToken(String token) {
        redisTemplate.delete(QUEUE_TOKEN_KEY + token);
        log.info("토큰 만료 처리 완료: token={}", token);
    }
}
