package kr.hhplus.be.server.service;

import org.springframework.stereotype.Service;

@Service
public interface DistributedLockService {
    boolean tryLock(String key, String value, long timeoutSeconds);
    void unlock(String key, String value);
}