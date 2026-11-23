# 🎫 콘서트 좌석 예약 서비스

> 인기 공연 티켓 오픈처럼 **트래픽이 한순간에 몰리는 상황**에서도 좌석 중복 예약과 잔액 오차 없이 동작하는 예약 API 서버

항해플러스 Lite 백엔드 과정에서 진행한 개인 프로젝트입니다. (2025.05 ~ 2025.07) 대기열 → 좌석 임시 배정 → 결제까지 이어지는 예약 흐름을 구현하고, 각 구간의 **동시성 제어**와 **이벤트 기반 후처리(Kafka)** 에 집중했습니다.

<br>

## 🛠 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Language / Framework | Java 17, Spring Boot 3.4, Spring Data JPA |
| Database | MySQL 8.0 |
| Cache / Lock / Queue | Redis 7 (Sorted Set, Set, 분산 락, Lua Script) |
| Messaging | Apache Kafka (3 브로커 클러스터, Zookeeper) |
| Test | JUnit 5, Testcontainers, spring-kafka-test |
| Docs | Springdoc OpenAPI (Swagger UI) |
| Infra (local) | Docker Compose |

<br>

## 🔑 핵심 기능

### 1. Redis 기반 대기열
- 토큰 발급 시 활성 사용자 수(`queue.max-active-users`, 기본 100명)를 확인해 **즉시 활성화** 또는 **대기열 진입**
- 대기열은 **Sorted Set**(진입 시각을 score로)으로 순번 관리, 활성 사용자는 **Set** + 사용자별 TTL 키로 관리
- 만료된 활성 사용자를 정리하고 대기 중인 사용자를 순차 활성화
- 세션 ID / 디바이스 핑거프린트로 **중복 토큰 발급 방지**
- `QueueTokenInterceptor`가 좌석 조회·예약·결제·잔액 API 진입 시 `Authorization: Bearer {token}`의 활성 여부를 검증

### 2. 좌석 임시 배정 (5분 홀드)
- 좌석 예약 시 해당 좌석을 **5분간 임시 배정**하고, 그 안에 결제하지 않으면 해제
- `ReservationScheduler`가 30초 주기로 만료된 임시 배정을 해제해 좌석을 다시 풀어줌

### 3. 잔액 충전 / 결제
- 잔액 충전·차감 시 거래 내역(`BalanceTransaction`) 기록
- 결제 성공 시 예약 확정, 환불·미결제 취소·재시도 로직 포함

### 4. 실시간 랭킹 / 통계
- 예약 완료·매진 이벤트를 받아 Redis Sorted Set에 **예약 속도 / 매진 속도 / 인기도 랭킹** 집계
- 원자성이 필요한 랭킹 갱신은 **Lua Script**로 처리
- 스케줄러로 5분 단위 배치 집계, 1시간 단위 전체 재계산

### 5. 외부 데이터 플랫폼 전송
- 예약 확정 데이터를 트랜잭션 커밋 후(`@TransactionalEventListener(AFTER_COMMIT)`) **비동기**로 외부 플랫폼에 전송
- 실패 건은 DB(`FailedDataTransfer`)에 저장하고 10분 주기로 **재시도**

<br>

## 🔒 동시성 제어

| 구간 | 문제 | 해결 방법 |
|---|---|---|
| 좌석 예약 | 같은 좌석에 동시 요청 → 중복 예약 | **Redis 분산 락**(`SETNX` + TTL) + 좌석 조회 시 **비관적 락**(`PESSIMISTIC_WRITE`) |
| 결제 | 같은 예약 이중 결제, 같은 사용자 잔액 동시 차감 | **예약 단위 + 사용자 잔액 단위** 분산 락을 순서대로 획득 후 처리 |
| 잔액 충전/차감 | Lost Update, 음수 잔액 | 잔액 조회 시 **비관적 락**으로 직렬화 |
| 락 해제 | 다른 요청이 잡은 락을 잘못 해제 | 락 value를 비교 후 삭제하는 **Lua Script**로 원자적 해제 |
| 임시 배정 만료 | 결제 안 한 좌석이 계속 묶임 | 스케줄러로 만료 건 주기적 해제 + 예약 시점에도 만료 여부 재확인 |

**테스트 결과** (자세한 내용: [동시성 제어 보고서](docs/concurrency_control_report.md))
- 잔액 100,000원 / 30,000원 결제 10건 동시 요청 → **3건 성공, 7건 실패, 최종 잔액 10,000원** (음수 잔액 없음)
- 좌석 5개 / 사용자 20명 동시 예약 → **5건만 성공, 중복 예약 0건**

<br>

## 📨 이벤트 아키텍처 (Kafka)

도메인 로직과 후처리(랭킹, 알림, 외부 전송)를 분리하기 위해 이벤트 기반으로 구성했습니다.

```
[예약/결제/잔액/대기열 서비스]
        │  도메인 이벤트 발행
        ▼
   ┌──────────── Kafka ────────────┐
   │ reservation-events            │
   │ payment-events                │
   │ balance-events                │
   │ concert-events                │
   │ queue-events                  │
   └──────┬───────────────┬────────┘
          ▼               ▼
  ranking-service   notification-service
  (랭킹/통계 갱신)     (알림 처리)
```

- 토픽: `reservation-events`, `payment-events`, `balance-events`, `concert-events`, `queue-events` 등
- Consumer Group을 **`ranking-service` / `notification-service`** 로 분리해 같은 이벤트를 독립적으로 소비
- 브로커 3대, 파티션 3개, replication factor 3 구성 (`docker-compose.yml`)
- Kafka 개념 정리 및 적용 과정: [docs/kafka.md](docs/kafka.md)

<br>

## 📁 프로젝트 구조

도메인별 패키지 + 레이어드 구조이며, 서비스는 UseCase 인터페이스를, 저장소는 Repository 인터페이스(도메인) / JPA 구현체로 분리했습니다.

```
src/main/java/kr/hhplus/be/server
├── queue/         # 대기열 토큰 발급·검증
├── concert/       # 콘서트 조회
├── seat/          # 좌석 조회
├── reservation/   # 좌석 임시 배정, 만료 스케줄러
├── payment/       # 결제, 환불
├── balance/       # 잔액 충전·차감, 거래 내역
├── ranking/       # 실시간 랭킹·통계 (Redis, Lua, Kafka Listener)
├── notification/  # 알림 이벤트 소비
├── external/      # 외부 데이터 플랫폼 전송·재시도
└── common/        # 분산 락, 인터셉터, Kafka/Redis 설정, 예외 처리
```

<br>

## 📡 주요 API

| Method | URI | 설명 | 토큰 |
|---|---|---|:---:|
| POST | `/api/queue/token` | 대기열 토큰 발급 | |
| GET | `/api/queue/status` | 대기 순번 조회 | |
| GET | `/api/concerts/available-dates` | 예약 가능 콘서트 조회 | |
| GET | `/api/concerts/{concertId}/seats` | 좌석 조회 | ✅ |
| POST | `/api/reservations` | 좌석 예약 (임시 배정) | ✅ |
| DELETE | `/api/reservations/{reservationId}` | 예약 취소 | ✅ |
| POST | `/api/users/{userId}/balance` | 잔액 충전 | ✅ |
| GET | `/api/users/{userId}/balance` | 잔액 조회 | ✅ |
| POST | `/api/payments` | 결제 | ✅ |
| GET | `/api/concerts/ranking` | 콘서트 랭킹 조회 | |

전체 명세: [API 명세서](docs/api-spec.md) · 실행 후 Swagger UI: `http://localhost:8080/swagger-ui.html`

<br>

## 🚀 실행 방법

```bash
# 1. 인프라 실행 (MySQL, Redis, Zookeeper, Kafka 브로커 3대)
docker-compose up -d

# 2. 애플리케이션 실행 (local 프로필)
./gradlew bootRun

# 3. 테스트 (Testcontainers 사용 → Docker 필요)
./gradlew test
```

| 서비스 | 포트 |
|---|---|
| MySQL | 3307 |
| Redis | 6379 |
| Kafka | 9092, 9093, 9094 |

<br>

## 📚 설계 문서

- [요구사항 명세서](docs/requirements.md)
- [API 명세서](docs/api-spec.md)
- [데이터베이스 ERD](docs/erd.md)
- [시퀀스 다이어그램](docs/sequence-diagrams.md)
- [시스템 아키텍처](docs/architecture.md)
- [동시성 제어 보고서](docs/concurrency_control_report.md)
- [Kafka 정리 및 적용](docs/kafka.md)
- [부하 테스트 시나리오](docs/부하테스트%20시나리오.md)
