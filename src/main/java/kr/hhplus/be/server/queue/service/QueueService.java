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

    // 세션 보안 관련 Redis 키 패턴
    private static final String USER_SESSION_KEY = "queue:session:";
    private static final String IP_LIMIT_KEY = "queue:ip:limit:";
    private static final String DEVICE_FINGERPRINT_KEY = "queue:device:";

    public QueueService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;

        // ObjectMapper 설정
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
    }

    /**
     * 대기열 토큰 발급 (기존 호환성 유지)
     *
     * @param userId 사용자 ID
     * @return 발급된 토큰 정보
     */
    public QueueToken issueToken(String userId) {
        // 기본 파라미터로 세션 기반 메서드 호출
        return issueTokenWithSession(userId, null, null, null, null);
    }

    /**
     * 세션 정보를 포함한 대기열 토큰 발급
     *
     * 기존 토큰 확인 시 세션 연속성을 검증하여 새로고침 등의
     * 비정상적인 접근을 차단
     *
     * @param userId 토큰을 발급받을 사용자의 고유 식별자
     * @param clientIp 클라이언트의 실제 IP 주소 (프록시 고려)
     * @param userAgent 클라이언트의 User-Agent 헤더 정보
     * @param sessionId 브라우저에서 생성된 세션 ID
     * @param deviceFingerprint 디바이스 고유 식별자 (핑거프린팅)
     * @return 발급된 대기열 토큰 정보 (QueueToken)
     * @throws RuntimeException IP 접속 제한 초과 시
     * @throws RuntimeException 디바이스 중복 접근 시
     * @throws RuntimeException 대기열 처리 중 락 획득 실패 시
     */
    public QueueToken issueTokenWithSession(String userId, String clientIp,
                                            String userAgent, String sessionId,
                                            String deviceFingerprint) {
        log.info("세션 기반 대기열 토큰 발급: userId={}, ip={}", userId, clientIp);

        // 1. IP별 접근 제한 확인
        if (clientIp != null && !checkIpLimit(clientIp)) {
            throw new RuntimeException("IP당 최대 접속 수를 초과했습니다.");
        }

        // 2. 디바이스 중복 접근 확인
        if (deviceFingerprint != null && !checkDeviceLimit(deviceFingerprint, userId)) {
            throw new RuntimeException("이미 다른 계정으로 접속 중입니다.");
        }

        // 3. 기존 세션 확인 (핵심 개선점)
        SessionInfo existingSession = getExistingSession(userId);
        if (existingSession != null) {
            // 세션 연속성 검증
            if (isValidSessionContinuity(existingSession, clientIp, userAgent, sessionId)) {
                // 기존 토큰 반환
                QueueToken existingToken = findExistingToken(userId);
                if (existingToken != null && !existingToken.isExpired()) {
                    log.info("유효한 세션으로 기존 토큰 반환: userId={}", userId);
                    return existingToken;
                }
            } else {
                // 새로고침이나 새 세션 -> 대기열 재진입
                log.info("세션 불일치로 인한 대기열 재진입: userId={}", userId);
                expireExistingSession(userId);
            }
        }

        // 4. 새로운 세션으로 토큰 발급
        return issueNewTokenWithSession(userId, clientIp, userAgent, sessionId, deviceFingerprint);
    }

    /**
     * 세션 연속성을 검증하여 정상적인 재접속인지 확인합니다.
     *
     * 브라우저 세션 ID, IP 주소, User-Agent, 마지막 활동 시간을
     * 종합적으로 검증하여 새로고침이나 비정상적인 접근을 탐지
     *
     * @param existingSession 기존에 저장된 세션 정보
     * @param clientIp 현재 요청의 클라이언트 IP 주소
     * @param userAgent 현재 요청의 User-Agent 헤더
     * @param sessionId 현재 요청의 브라우저 세션 ID
     * @return 세션이 연속성을 유지하고 있으면 true, 그렇지 않으면 false
     */
    private boolean isValidSessionContinuity(SessionInfo existingSession,
                                             String clientIp, String userAgent, String sessionId) {
        // 파라미터가 null인 경우 검증 생략 (기본 호환성)
        if (sessionId == null || clientIp == null || userAgent == null) {
            return true;
        }

        // 브라우저 세션 ID 확인
        if (!existingSession.getSessionId().equals(sessionId)) {
            log.info("브라우저 세션 ID 불일치");
            return false;
        }

        // IP 변경 확인 (모바일 환경 고려)
        if (!existingSession.getClientIp().equals(clientIp)) {
            log.info("IP 주소 변경 감지");
            return false;
        }

        // User-Agent 변경 확인
        if (!existingSession.getUserAgent().equals(userAgent)) {
            log.info("User-Agent 변경 감지");
            return false;
        }

        // 마지막 활동 시간 확인 (30분 이내)
        if (existingSession.getLastActivity().isBefore(LocalDateTime.now().minusMinutes(30))) {
            log.info("세션 비활성 시간 초과");
            return false;
        }

        return true;
    }

    /**
     * IP 주소별 동시 접속 제한을 확인합니다.
     *
     * 하나의 IP에서 과도한 동시 접속을 방지하여
     * 대기열의 공정성을 보장
     *
     * @param clientIp 확인할 클라이언트 IP 주소
     * @return 접속 가능하면 true, 제한 초과 시 false
     */
    private boolean checkIpLimit(String clientIp) {
        String ipLimitKey = IP_LIMIT_KEY + clientIp;
        String currentCount = (String) redisTemplate.opsForValue().get(ipLimitKey);

        int count = currentCount != null ? Integer.parseInt(currentCount) : 0;
        if (count >= 3) { // IP당 최대 3개 세션
            log.warn("IP 접속 제한 초과: ip={}, count={}", clientIp, count);
            return false;
        }

        // 카운트 증가
        redisTemplate.opsForValue().increment(ipLimitKey);
        redisTemplate.expire(ipLimitKey, 300, TimeUnit.SECONDS); // 5분 TTL

        log.info("IP 접속 허용: ip={}, count={}", clientIp, count + 1);
        return true;
    }

    /**
     * 디바이스 중복 접근을 확인하고 제한합니다.
     *
     * 하나의 디바이스에서 여러 계정으로 동시 접속하는 것을
     * 방지하여 부정한 대기열 진입을 차단
     *
     * @param deviceFingerprint 디바이스 고유 식별자
     * @param userId 현재 요청한 사용자 ID
     * @return 접속 가능하면 true, 다른 계정이 사용 중이면 false
     */
    private boolean checkDeviceLimit(String deviceFingerprint, String userId) {
        String deviceKey = DEVICE_FINGERPRINT_KEY + deviceFingerprint;
        String existingUserId = (String) redisTemplate.opsForValue().get(deviceKey);

        if (existingUserId != null && !existingUserId.equals(userId)) {
            log.warn("디바이스 중복 접근 차단: device={}, existing={}, current={}",
                    deviceFingerprint, existingUserId, userId);
            return false; // 다른 사용자가 같은 디바이스 사용 중
        }

        // 디바이스-사용자 매핑 저장
        redisTemplate.opsForValue().set(deviceKey, userId, tokenExpireMinutes, TimeUnit.MINUTES);
        log.info("디바이스 사용자 매핑 저장: device={}, userId={}", deviceFingerprint, userId);
        return true;
    }

    /**
     * 새로운 토큰과 세션을 발급합니다.
     *
     * 세션 정보를 생성하여 저장하고 기존 토큰 발급 로직을
     * 사용하여 새로운 대기열 토큰을 발급
     *
     * @param userId 토큰을 발급받을 사용자 ID
     * @param clientIp 클라이언트 IP 주소
     * @param userAgent User-Agent 헤더 정보
     * @param sessionId 브라우저 세션 ID
     * @param deviceFingerprint 디바이스 핑거프린트
     * @return 새로 발급된 대기열 토큰 정보
     */
    private QueueToken issueNewTokenWithSession(String userId, String clientIp,
                                                String userAgent, String sessionId,
                                                String deviceFingerprint) {
        // 세션 정보 생성 (null 체크 포함)
        SessionInfo sessionInfo = new SessionInfo(
                sessionId != null ? sessionId : "unknown",
                clientIp != null ? clientIp : "unknown",
                userAgent != null ? userAgent : "unknown",
                deviceFingerprint != null ? deviceFingerprint : "unknown",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        // 세션 정보 저장
        saveSessionInfo(userId, sessionInfo);

        // 분산 락 획득 시도
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
     * 기존 토큰 조회
     *
     * @param userId 사용자 ID
     * @return 기존 토큰 정보, 없으면 null
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
     * 대기열 상태 조회
     *
     * @param token 조회할 토큰
     * @return 대기열 상태 정보
     * @throws QueueTokenNotFoundException 토큰을 찾을 수 없는 경우
     * @throws QueueTokenExpiredException 토큰이 만료된 경우
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
     * 세션 검증을 포함한 대기열 상태 조회
     *
     * @param token 조회할 토큰
     * @param clientIp 클라이언트 IP
     * @param userAgent User-Agent
     * @param sessionId 세션 ID
     * @return 대기열 상태 정보
     * @throws QueueTokenNotFoundException 토큰을 찾을 수 없는 경우
     * @throws QueueTokenExpiredException 토큰이 만료된 경우
     */
    public QueueToken getQueueStatusWithSession(String token, String clientIp,
                                                String userAgent, String sessionId) {
        QueueToken queueToken = getQueueStatus(token);

        // 세션 정보가 제공된 경우 추가 검증
        if (clientIp != null && userAgent != null && sessionId != null) {
            SessionInfo existingSession = getExistingSession(queueToken.getUserId());
            if (existingSession != null) {
                if (!isValidSessionContinuity(existingSession, clientIp, userAgent, sessionId)) {
                    log.warn("세션 불일치로 토큰 만료 처리: token={}", token);
                    expireToken(token, queueToken.getUserId());
                    throw new QueueTokenExpiredException("세션이 유효하지 않습니다.");
                }

                // 마지막 활동 시간 업데이트
                existingSession.setLastActivity(LocalDateTime.now());
                saveSessionInfo(queueToken.getUserId(), existingSession);
            }
        }

        return queueToken;
    }

    /**
     * 토큰 유효성 검증 (좌석 조회 시 사용)
     *
     * @param token 검증할 토큰
     * @return 유효한 활성 토큰이면 true, 그렇지 않으면 false
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

    /**
     * 락이 획득된 상태에서 대기 사용자들을 활성화
     */
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
     *
     * @param queueToken 업데이트할 토큰
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
     *
     * @param userId 사용자 ID
     * @param token 토큰
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
     * 세션 정보를 Redis에 저장합니다.
     *
     * <p>사용자의 세션 정보를 JSON 형태로 직렬화하여
     * Redis에 저장하고 토큰 만료 시간과 동일한 TTL을 설정합니다.</p>
     *
     * @param userId 세션 정보를 저장할 사용자 ID
     * @param sessionInfo 저장할 세션 정보 객체
     * @throws RuntimeException 세션 정보 저장 실패 시
     */
    private void saveSessionInfo(String userId, SessionInfo sessionInfo) {
        String sessionKey = USER_SESSION_KEY + userId;
        try {
            String json = objectMapper.writeValueAsString(sessionInfo);
            redisTemplate.opsForValue().set(sessionKey, json, tokenExpireMinutes, TimeUnit.MINUTES);
            log.info("세션 정보 저장 완료: userId={}", userId);
        } catch (Exception e) {
            log.error("세션 정보 저장 실패: userId={}", userId, e);
            throw new RuntimeException("세션 정보 저장 실패", e);
        }
    }

    /**
     * Redis에서 기존 세션 정보를 조회합니다.
     *
     * <p>저장된 세션 정보를 조회하여 SessionInfo 객체로
     * 역직렬화합니다. 조회 실패 시 null을 반환합니다.</p>
     *
     * @param userId 세션 정보를 조회할 사용자 ID
     * @return 조회된 세션 정보 객체, 없으면 null
     */
    private SessionInfo getExistingSession(String userId) {
        String sessionKey = USER_SESSION_KEY + userId;
        try {
            Object obj = redisTemplate.opsForValue().get(sessionKey);
            if (obj == null) return null;

            if (obj instanceof String) {
                return objectMapper.readValue((String) obj, SessionInfo.class);
            }
            return objectMapper.convertValue(obj, SessionInfo.class);
        } catch (Exception e) {
            log.error("세션 정보 조회 실패: userId={}", userId, e);
            return null;
        }
    }

    /**
     * 기존 세션을 만료 처리하고 관련 정보를 정리합니다.
     *
     * <p>새로고침이나 세션 불일치가 감지된 경우
     * 기존 토큰과 세션 정보를 모두 삭제합니다.</p>
     *
     * @param userId 세션을 만료할 사용자 ID
     */
    private void expireExistingSession(String userId) {
        // 기존 토큰 만료
        String existingToken = (String) redisTemplate.opsForValue().get(USER_TOKEN_MAPPING_KEY + userId);
        if (existingToken != null) {
            expireToken(existingToken, userId);
        }

        // 세션 정보 삭제
        redisTemplate.delete(USER_SESSION_KEY + userId);

        log.info("기존 세션 만료 처리 완료: userId={}", userId);
    }

    /**
     * 토큰 만료 처리
     *
     * @param token 만료할 토큰
     * @param userId 사용자 ID
     */
    private void expireToken(String token, String userId) {
        // 토큰 삭제
        redisTemplate.delete(QUEUE_TOKEN_KEY + token);

        // 사용자-토큰 매핑 삭제
        redisTemplate.delete(USER_TOKEN_MAPPING_KEY + userId);

        // 활성 사용자에서 제거
        redisTemplate.opsForSet().remove(ACTIVE_USERS_KEY, userId);
        redisTemplate.delete(USER_ACTIVE_KEY_PREFIX + userId);

        // 세션 정보 삭제
        redisTemplate.delete(USER_SESSION_KEY + userId);

        // IP 제한 카운트 감소
        // 디바이스 매핑 삭제는 자동 만료로 처리

        log.info("토큰 만료 처리 완료: token={}, userId={}", token, userId);
    }

    /**
     * 분산 락 획득
     *
     * @param lockKey 락 키
     * @param lockValue 락 값
     * @param timeoutSeconds 타임아웃 (초)
     * @return 락 획득 성공 시 true, 실패 시 false
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
     *
     * @param lockKey 락 키
     * @param lockValue 락 값
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
     *
     * @param key Redis 키
     * @return 조회된 토큰 객체, 없으면 null
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
     *
     * @param key Redis 키
     * @param queueToken 저장할 토큰 객체
     * @throws RuntimeException 저장 실패 시
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

    /**
     * 세션 정보를 담는 내부 클래스
     *
     * <p>사용자의 세션 연속성 검증을 위한 정보들을 저장합니다.
     * Redis에 JSON 형태로 직렬화되어 저장됩니다.</p>
     */
    public static class SessionInfo {
        /** 브라우저에서 생성된 세션 ID */
        private String sessionId;

        /** 클라이언트의 실제 IP 주소 */
        private String clientIp;

        /** 클라이언트의 User-Agent 헤더 정보 */
        private String userAgent;

        /** 디바이스 고유 식별자 (핑거프린팅) */
        private String deviceFingerprint;

        /** 세션 생성 시간 */
        private LocalDateTime createdAt;

        /** 마지막 활동 시간 */
        private LocalDateTime lastActivity;

        /**
         * SessionInfo 생성자
         *
         * @param sessionId 브라우저 세션 ID
         * @param clientIp 클라이언트 IP 주소
         * @param userAgent User-Agent 헤더
         * @param deviceFingerprint 디바이스 핑거프린트
         * @param createdAt 세션 생성 시간
         * @param lastActivity 마지막 활동 시간
         */
        public SessionInfo(String sessionId, String clientIp, String userAgent,
                           String deviceFingerprint, LocalDateTime createdAt, LocalDateTime lastActivity) {
            this.sessionId = sessionId;
            this.clientIp = clientIp;
            this.userAgent = userAgent;
            this.deviceFingerprint = deviceFingerprint;
            this.createdAt = createdAt;
            this.lastActivity = lastActivity;
        }

        /**
         * 기본 생성자 (Jackson 직렬화용)
         */
        public SessionInfo() {}

        // Getter 메서드들

        /**
         * 브라우저 세션 ID를 반환합니다.
         * @return 세션 ID
         */
        public String getSessionId() { return sessionId; }

        /**
         * 클라이언트 IP 주소를 반환합니다.
         * @return IP 주소
         */
        public String getClientIp() { return clientIp; }

        /**
         * User-Agent 헤더 정보를 반환합니다.
         * @return User-Agent 문자열
         */
        public String getUserAgent() { return userAgent; }

        /**
         * 디바이스 핑거프린트를 반환합니다.
         * @return 디바이스 핑거프린트
         */
        public String getDeviceFingerprint() { return deviceFingerprint; }

        /**
         * 세션 생성 시간을 반환합니다.
         * @return 생성 시간
         */
        public LocalDateTime getCreatedAt() { return createdAt; }

        /**
         * 마지막 활동 시간을 반환합니다.
         * @return 마지막 활동 시간
         */
        public LocalDateTime getLastActivity() { return lastActivity; }

        // Setter 메서드들 (Jackson 직렬화용)

        /**
         * 브라우저 세션 ID를 설정합니다.
         * @param sessionId 세션 ID
         */
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        /**
         * 클라이언트 IP 주소를 설정합니다.
         * @param clientIp IP 주소
         */
        public void setClientIp(String clientIp) { this.clientIp = clientIp; }

        /**
         * User-Agent 헤더 정보를 설정합니다.
         * @param userAgent User-Agent 문자열
         */
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

        /**
         * 디바이스 핑거프린트를 설정합니다.
         * @param deviceFingerprint 디바이스 핑거프린트
         */
        public void setDeviceFingerprint(String deviceFingerprint) {
            this.deviceFingerprint = deviceFingerprint;
        }

        /**
         * 세션 생성 시간을 설정합니다.
         * @param createdAt 생성 시간
         */
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        /**
         * 마지막 활동 시간을 설정합니다.
         * @param lastActivity 마지막 활동 시간
         */
        public void setLastActivity(LocalDateTime lastActivity) {
            this.lastActivity = lastActivity;
        }
    }
}