package kr.hhplus.be.server.common.lock;

import org.springframework.stereotype.Service;

@Service
public interface DistributedLockService {
    boolean tryLock(String key, String value, long timeoutSeconds);
    void unlock(String key, String value);
}