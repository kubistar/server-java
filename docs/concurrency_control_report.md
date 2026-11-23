# 콘서트 예약 서비스 동시성 제어 보고서

## 1. 문제 상황 분석

### 1.1 좌석 중복 예약
- **상황**: 같은 좌석에 여러 사용자가 동시에 예약 요청
- **문제**: 조회 시점엔 모두 `AVAILABLE`로 보여서 여러 명이 같은 좌석을 배정받음
- **영향**: 데이터 무결성 훼손, 고객 불만

### 1.2 중복 결제 / 잔액 동시 차감
- **상황 1**: 같은 예약에 대해 결제 요청이 두 번 이상 동시에 들어옴 (더블 클릭, 재시도)
- **상황 2**: 같은 사용자의 잔액에서 여러 결제가 동시에 차감됨
- **문제**: 잔액 조회 → 검증 → 차감 사이에 다른 트랜잭션이 끼어들어 Lost Update, 음수 잔액 발생
- **영향**: 실제 보유 금액보다 많은 결제 처리

### 1.3 임시 배정 만료 처리
- **상황**: 좌석을 임시 배정(5분)받고 결제하지 않음
- **문제**: 만료된 좌석이 해제되지 않으면 다른 사용자가 예약 불가
- **영향**: 실제 예약 가능 좌석 감소

---

## 2. 해결 전략

### 2.1 좌석 예약: Redis 분산 락 + DB 비관적 락

```java
// ReservationService.reserveSeat()
String lockKey = String.format("seat_lock:%d:%d", concertId, seatNumber);

if (!distributedLockService.tryLock(lockKey, userId, 10)) {   // 1차: Redis 분산 락
    throw new RuntimeException("다른 사용자가 처리 중입니다. 잠시 후 재시도해주세요.");
}
try {
    Seat seat = seatRepository.findByConcertIdAndSeatNumberWithLock(concertId, seatNumber); // 2차: SELECT ... FOR UPDATE

    if (!seat.isAvailable()) {
        if (seat.isExpired()) seat.releaseAssignment();      // 만료된 배정이면 해제 후 진행
        else throw new RuntimeException("이미 다른 사용자가 선택한 좌석입니다.");
    }
    seat.assignTemporarily(userId, now.plusMinutes(5));       // 5분 임시 배정
    reservationRepository.save(new Reservation(...));
    eventPublisher.publishEvent(new ReservationCompletedEvent(...));
} finally {
    distributedLockService.unlock(lockKey, userId);
}
```

```java
// SeatJpaRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM Seat s WHERE s.concertId = :concertId AND s.seatNumber = :seatNumber")
Optional<Seat> findByConcertIdAndSeatNumberWithLock(...);
```

**선택 이유**
- **분산 락(1차)**: 서버가 여러 대여도 같은 좌석 요청을 앞단에서 걸러내 DB 커넥션·락 대기를 줄임. 락을 못 잡으면 즉시 실패(Fail-fast)
- **비관적 락(2차)**: 분산 락 TTL 만료, Redis 장애 등으로 1차가 뚫려도 DB 레벨에서 최종 정합성 보장

### 2.2 결제: 예약 락 → 잔액 락 순서로 분산 락 획득

```java
// PaymentService.processPayment()
String reservationLockKey = "payment:reservation:" + reservationId;
String balanceLockKey     = "payment:balance:" + userId;

if (!distributedLockService.tryLock(reservationLockKey, lockValue, 10)) {   // 같은 예약 중복 결제 방지
    throw new IllegalArgumentException("해당 예약에 대한 결제가 이미 진행 중입니다.");
}
try {
    if (!distributedLockService.tryLock(balanceLockKey, lockValue, 10)) {   // 같은 사용자 잔액 동시 차감 방지
        throw new IllegalArgumentException("사용자의 다른 결제가 진행 중입니다.");
    }
    try {
        return processPaymentWithLock(command);   // 예약 검증 → 결제 생성 → 잔액 차감 → 예약/좌석 확정
    } finally {
        distributedLockService.unlock(balanceLockKey, lockValue);
    }
} finally {
    distributedLockService.unlock(reservationLockKey, lockValue);
}
```

**선택 이유**
- **락 단위를 둘로 나눔**: "같은 예약을 두 번 결제"와 "같은 사용자 잔액을 동시에 차감"은 다른 문제라서 키를 분리
- **락 획득 순서 고정**: 항상 예약 → 잔액 순서로 잡아 데드락 가능성 제거
- **보상 처리**: 잔액 차감 이후 단계(예약 확정, 좌석 확정)에서 실패하면 `rollbackPayment()`로 잔액 환불

### 2.3 잔액 충전/차감: 비관적 락

```java
// BalanceRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT b FROM Balance b WHERE b.userId = :userId")
Optional<Balance> findByUserIdWithLock(@Param("userId") String userId);

// BalanceService.deductBalance()
@Transactional
public Balance deductBalance(String userId, BigDecimal amount, String reservationId) {
    Balance balance = balanceRepository.findByUserIdWithLock(userId).orElseThrow(...);

    if (balance.getAmount().compareTo(amount) < 0) {
        throw new IllegalArgumentException("잔액이 부족합니다.");
    }
    balance.deductAmount(amount);
    balanceRepository.save(balance);
    balanceTransactionRepository.save(BalanceTransaction.payment(...));   // 거래 내역 기록
    return balance;
}
```

**선택 이유**
- 잔액은 **돈**이라 충돌 시 재시도보다 **확실한 직렬화**가 우선 → 낙관적 락 대신 비관적 락
- 충전(`chargeBalance`)도 같은 락으로 조회해 충전·차감이 동시에 일어나도 Lost Update 없음
- 결제 경로에서는 2.2의 사용자 단위 분산 락이 앞단에서 먼저 직렬화하고, 비관적 락이 DB 레벨 최종 방어선 역할

### 2.4 락 해제: Lua Script로 원자적 해제

```java
// RedisDistributedLockService
public boolean tryLock(String key, String value, long timeoutSeconds) {
    return redisTemplate.opsForValue().setIfAbsent(key, value, timeoutSeconds, TimeUnit.SECONDS); // SET NX EX
}

private static final String UNLOCK_SCRIPT =
    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
    "    return redis.call('del', KEYS[1]) " +
    "else return 0 end";
```

- **TTL**: 서버가 죽어도 락이 자동 해제되어 영구 데드락 방지
- **value 비교 후 삭제**: 내 작업이 길어져 TTL이 만료되고 다른 요청이 락을 잡은 경우, 남의 락을 지우지 않도록 `GET` + `DEL`을 Lua로 원자 실행

### 2.5 임시 배정 만료: 스케줄러 + 예약 시점 재확인

```java
// ReservationScheduler
@Scheduled(fixedDelay = 30000)   // 30초마다
public void releaseExpiredReservations() {
    reserveSeatUseCase.releaseExpiredReservations();   // TEMPORARILY_ASSIGNED && expiresAt < now → 해제
}
```

- 스케줄러가 주기적으로 만료 건을 해제
- 스케줄러 주기(30초) 사이의 공백은 **예약 요청 시 `seat.isExpired()`를 다시 확인**해서 메움 → 만료된 좌석은 바로 재배정 가능

### 2.6 검토했지만 채택하지 않은 방식: 조건부 UPDATE

```java
@Modifying
@Query("UPDATE Balance b SET b.amount = b.amount - :amount " +
       "WHERE b.userId = :userId AND b.amount >= :amount")
int deductAmountByUserId(...);
```

- 락 없이 SQL 한 줄로 검증+차감을 원자 처리할 수 있어 성능상 유리
- `BalanceService.deductBalanceWithCondition()`으로 구현은 해뒀지만 **결제 경로에서는 사용하지 않음**
- 미채택 이유: 차감 후 잔액을 다시 조회해야 거래 내역(`balanceAfter`)을 남길 수 있고, 잔액 부족과 사용자 없음을 구분하기 어려움. 엔티티 기반 도메인 로직(`Balance.deductAmount()`)을 유지하는 쪽을 선택

---

## 3. 사용 기법 요약

| 구간 | 1차 제어 | 2차 제어 | 비고 |
|---|---|---|---|
| 좌석 예약 | Redis 분산 락 (좌석 단위) | `PESSIMISTIC_WRITE` (Seat) | 락 실패 시 즉시 실패 |
| 결제 | Redis 분산 락 (예약 단위 → 사용자 단위) | `PESSIMISTIC_WRITE` (Balance) | 실패 시 보상 환불 |
| 잔액 충전 | - | `PESSIMISTIC_WRITE` (Balance) | |
| 임시 배정 만료 | 30초 주기 스케줄러 | 예약 시점 만료 재확인 | |

- **Isolation Level**: MySQL 기본(REPEATABLE READ), 정합성은 명시적 락으로 보장

---

## 4. 테스트 결과

### 4.1 잔액 차감 동시성 (`PointConcurrencyTest.concurrentBalanceDeduction_DirectPayment_ShouldPreventNegativeBalance`)

**시나리오**
- 초기 잔액 100,000원, 30,000원짜리 예약 10건에 대해 **10개 스레드가 동시에 결제**
- 기대 결과: 최대 3건 성공, 최종 잔액 10,000원, 음수 잔액 없음

**결과**
```
Success count: 3, Fail count: 7
10개 스레드 동시 결제 소요시간: 1302ms
최종 잔액: 10,000원 (예상 잔액: 10,000원)
```

- ✅ 음수 잔액 없음
- ✅ 성공 건수 × 결제 금액 = 실제 차감액
- 실패 7건은 사용자 단위 분산 락 획득 실패 또는 잔액 부족으로 정상 거절

### 4.2 분산 락 동시성 (`SeatReservationConcurrencyTest`)

| 테스트 | 시나리오 | 검증 조건 |
|---|---|---|
| `락_동시성_테스트` | 100개 스레드가 같은 좌석 락 키에 동시 `tryLock` (락 보유 중 200ms 작업) | 성공 **1건**, 실패 **99건** |
| `락_순차_해제_테스트` | 10개 스레드가 락 획득 실패 시 100ms 후 재시도 | 10건 모두 **순차 처리** 완료 |

- ✅ 같은 키에 대해 한 번에 하나의 요청만 락 획득
- ✅ 락 해제 후 대기 요청이 정상적으로 이어서 획득

### 4.3 만료 예약 해제 (`ReservationSchedulerTest`)

- 만료 시각이 지난 임시 배정 예약을 만든 뒤 `releaseExpiredReservations()` 직접 호출
- ✅ 예약 상태 `EXPIRED`로 변경
- ✅ 좌석 `AVAILABLE` 복구, 배정 사용자 정보 제거

---

## 5. 알려진 한계와 개선 방향

### 5.1 분산 락 해제가 트랜잭션 커밋보다 먼저 일어남
- `ReservationService`는 클래스 레벨 `@Transactional`이라, 메서드 안 `finally`에서 분산 락을 해제하면 **커밋 전에 락이 풀림**
- 그 틈에 다음 요청이 락을 잡으면 아직 커밋 안 된 좌석 상태를 볼 수 있음
- 현재는 **비관적 락이 커밋 시점까지 행을 잡고 있어서 중복 예약은 발생하지 않음**
- **개선**: 락 획득/해제를 트랜잭션 바깥(Facade 계층 또는 AOP)으로 옮겨 `락 획득 → 트랜잭션 시작 → 커밋 → 락 해제` 순서 보장

### 5.2 결제 로직의 `@Transactional`이 적용되지 않음
- `processPaymentWithLock()`은 같은 클래스 안에서 호출(self-invocation)되기 때문에 Spring 프록시를 거치지 않아 `@Transactional`이 **무시됨**
- 결과적으로 결제 저장, 잔액 차감(`BalanceService`의 별도 트랜잭션), 예약 확정, 좌석 확정이 **각각 따로 커밋**됨
- 중간 실패는 `rollbackPayment()` 보상 로직으로 잔액을 되돌리지만, 결제·예약 상태까지 원자적으로 롤백되지는 않음
- **개선**: 트랜잭션 로직을 별도 빈으로 분리하거나 Facade에서 락을 잡고 내부 서비스 메서드를 트랜잭션으로 호출

### 5.3 락 대기 없이 즉시 실패
- `tryLock`은 한 번만 시도하고 실패하면 바로 예외 → 순간적인 경합에도 사용자가 실패를 받음
- **개선**: 좌석처럼 "먼저 온 사람만 성공"이 맞는 곳은 그대로 두고, 결제처럼 같은 사용자의 정상 재시도가 있는 곳은 짧은 스핀/백오프 재시도 또는 Redisson Pub/Sub 락 검토

### 5.4 테스트 범위
- 현재 테스트는 JUnit 멀티스레드 기반의 **코드 레벨 정합성 검증**
- 실제 TPS, 커넥션 풀 고갈, 락 경합률은 [부하 테스트 시나리오](./부하테스트%20시나리오.md) 기반의 별도 부하 테스트로 확인 필요
