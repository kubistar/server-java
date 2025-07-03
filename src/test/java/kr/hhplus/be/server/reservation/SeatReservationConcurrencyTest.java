package kr.hhplus.be.server.reservation;

import kr.hhplus.be.server.common.lock.DistributedLockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class SeatReservationConcurrencyTest {

    @Autowired
    private DistributedLockService distributedLockService;

    @Test
    public void 락_동시성_테스트() throws InterruptedException {
        // 테스트 설정
        String lockKey = "test:lock:seat:1";
        int threadCount = 100;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1); // 동시 시작을 위한 래치
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        System.out.println("=== 락 동시성 테스트 시작 ===");
        System.out.println("스레드 수: " + threadCount);
        System.out.println("락 키: " + lockKey);

        // 모든 스레드 생성
        for (int i = 0; i < threadCount; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    // 모든 스레드가 동시에 시작하도록 대기
                    startLatch.await();

                    String userLockValue = "user-" + userId;
                    long startTime = System.currentTimeMillis();

                    if (distributedLockService.tryLock(lockKey, userLockValue, 30)) {
                        try {
                            long lockAcquiredTime = System.currentTimeMillis();
                            System.out.println("✅ 락 획득 성공: " + userLockValue +
                                    " (대기시간: " + (lockAcquiredTime - startTime) + "ms)");

                            // 실제 작업 시뮬레이션 (200ms)
                            Thread.sleep(200);
                            successCount.incrementAndGet();

                        } finally {
                            distributedLockService.unlock(lockKey, userLockValue);
                            System.out.println("🔓 락 해제 완료: " + userLockValue);
                        }
                    } else {
                        long failTime = System.currentTimeMillis();
                        System.out.println("❌ 락 획득 실패: " + userLockValue +
                                " (대기시간: " + (failTime - startTime) + "ms)");
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("🚨 오류 발생: user-" + userId + " - " + e.getMessage());
                    e.printStackTrace();
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 모든 스레드 동시 시작
        System.out.println("모든 스레드 동시 시작!");
        startLatch.countDown();

        // 모든 스레드 완료 대기 (최대 60초)
        boolean completed = endLatch.await(60, java.util.concurrent.TimeUnit.SECONDS);

        executor.shutdown();

        // 결과 출력
        System.out.println("\n=== 테스트 결과 ===");
        System.out.println("완료 여부: " + (completed ? "✅ 성공" : "❌ 타임아웃"));
        System.out.println("성공 수: " + successCount.get());
        System.out.println("실패 수: " + failCount.get());
        System.out.println("전체 수: " + (successCount.get() + failCount.get()));

        // 검증: 정확히 1개만 성공해야 함
        assertEquals(1, successCount.get(),
                "락이 제대로 작동한다면 1개만 성공해야 합니다!");
        assertEquals(threadCount - 1, failCount.get(),
                "나머지는 모두 실패해야 합니다!");

        System.out.println("🎉 동시성 테스트 통과!");
    }

    @Test
    public void 락_순차_해제_테스트() throws InterruptedException {
        // 락이 순차적으로 해제되는지 테스트
        String lockKey = "test:lock:sequential";
        int threadCount = 10;
        AtomicInteger processedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);

        System.out.println("=== 순차 처리 테스트 시작 ===");

        for (int i = 0; i < threadCount; i++) {
            final int userId = i;
            new Thread(() -> {
                try {
                    String userLockValue = "user-" + userId;

                    // 락 획득까지 최대 30초 대기
                    boolean acquired = false;
                    long startTime = System.currentTimeMillis();

                    while (!acquired && (System.currentTimeMillis() - startTime) < 30000) {
                        acquired = distributedLockService.tryLock(lockKey, userLockValue, 5);
                        if (!acquired) {
                            Thread.sleep(100); // 100ms 대기 후 재시도
                        }
                    }

                    if (acquired) {
                        try {
                            int currentOrder = processedCount.incrementAndGet();
                            System.out.println("🔒 처리 순서 " + currentOrder + ": " + userLockValue);
                            Thread.sleep(500); // 작업 시뮬레이션
                        } finally {
                            distributedLockService.unlock(lockKey, userLockValue);
                        }
                    } else {
                        System.out.println("⏰ 타임아웃: " + userLockValue);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();

        System.out.println("처리된 요청 수: " + processedCount.get());
        assertEquals(threadCount, processedCount.get(), "모든 요청이 순차적으로 처리되어야 합니다!");

        System.out.println("🎉 순차 처리 테스트 통과!");
    }

    @Test
    public void Redis_연결_테스트() {
        System.out.println("=== Redis 연결 테스트 ===");

        String testKey = "test:connection:" + System.currentTimeMillis();
        String testValue = "test-value";

        try {
            // 락 획득 테스트
            boolean acquired = distributedLockService.tryLock(testKey, testValue, 10);
            System.out.println("락 획득: " + (acquired ? "✅ 성공" : "❌ 실패"));

            if (acquired) {
                // 락 해제 테스트
                distributedLockService.unlock(testKey, testValue);
                System.out.println("락 해제: ✅ 완료");

                // 다시 획득 가능한지 테스트
                boolean reacquired = distributedLockService.tryLock(testKey, testValue + "2", 10);
                System.out.println("재획득: " + (reacquired ? "✅ 성공" : "❌ 실패"));

                if (reacquired) {
                    distributedLockService.unlock(testKey, testValue + "2");
                }
            }

            System.out.println("🎉 Redis 연결 테스트 통과!");

        } catch (Exception e) {
            System.err.println("🚨 Redis 연결 실패: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}