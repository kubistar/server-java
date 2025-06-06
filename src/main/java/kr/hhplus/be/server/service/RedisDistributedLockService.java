package kr.hhplus.be.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
public class RedisDistributedLockService implements DistributedLockService {

    private static final Logger logger = LoggerFactory.getLogger(RedisDistributedLockService.class);

    private final RedisTemplate<String, String> redisTemplate;

    // Lua 스크립트로 원자적 unlock 보장
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "    return redis.call('del', KEYS[1]) " +
                    "else " +
                    "    return 0 " +
                    "end";

    public RedisDistributedLockService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(String key, String value, long timeoutSeconds) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, value, timeoutSeconds, TimeUnit.SECONDS);

            boolean result = Boolean.TRUE.equals(acquired);

            if (result) {
                logger.debug("분산 락 획득 성공: key={}, value={}", key, value);
            } else {
                logger.debug("분산 락 획득 실패: key={}, value={}", key, value);
            }

            return result;

        } catch (Exception e) {
            logger.error("분산 락 획득 중 오류 발생: key={}, value={}", key, value, e);
            return false;
        }
    }

    @Override
    public void unlock(String key, String value) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(UNLOCK_SCRIPT);
            script.setResultType(Long.class);

            Long result = redisTemplate.execute(script, Collections.singletonList(key), value);

            if (result != null && result == 1) {
                logger.debug("분산 락 해제 성공: key={}, value={}", key, value);
            } else {
                logger.warn("분산 락 해제 실패 또는 이미 만료됨: key={}, value={}", key, value);
            }

        } catch (Exception e) {
            logger.error("분산 락 해제 중 오류 발생: key={}, value={}", key, value, e);
        }
    }
}
