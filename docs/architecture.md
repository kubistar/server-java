# 시스템 아키텍처

## 📋 개요

콘서트 예약 서비스의 인프라 구성과 애플리케이션 내부 이벤트 흐름을 정리한 문서입니다.

- **1. 로컬 개발 구성**: `docker-compose.yml`로 실제 띄우는 구성
- **2. 운영 목표 구성**: 수평 확장을 전제로 설계한 배포 구성 (미구축)
- **3. 이벤트 흐름**: 도메인 이벤트 → Kafka → Consumer 구조

---

## 1. 로컬 개발 구성 (docker-compose)

```mermaid
graph LR
    App[🚀 Spring Boot App<br/>:8080]

    subgraph Docker["Docker Compose (hhplus-network)"]
        MySQL[(🗄️ MySQL 8.0<br/>:3307)]
        Redis[(🔴 Redis 7<br/>:6379)]
        ZK[Zookeeper<br/>:2181]
        subgraph Kafka["Kafka Cluster"]
            B1[Broker 1<br/>:9092]
            B2[Broker 2<br/>:9093]
            B3[Broker 3<br/>:9094]
        end
    end

    App --> MySQL
    App --> Redis
    App --> B1 & B2 & B3
    ZK --- B1 & B2 & B3
```

| 구성 요소 | 역할 |
|---|---|
| **MySQL** | 콘서트·좌석·예약·결제·잔액·거래 내역 저장, 좌석/잔액 조회 시 비관적 락 |
| **Redis** | 대기열(Sorted Set), 활성 사용자(Set + TTL), 분산 락, 실시간 랭킹(Sorted Set + Lua) |
| **Kafka** | 도메인 이벤트 전달 (브로커 3대, 파티션 3, replication factor 3, min ISR 2) |
| **Zookeeper** | Kafka 클러스터 메타데이터 관리 |

---

## 2. 운영 목표 구성

> 아래는 트래픽 증가를 가정한 **설계안**이며 실제로 구축하지는 않았습니다. 현재 코드에는 Read/Write DB 라우팅이 구현되어 있지 않습니다.

```mermaid
graph LR
    subgraph Client["사용자"]
        User[👤 일반 사용자]
    end

    LB[⚖️ Load Balancer]

    subgraph Private["Private Network"]
        subgraph AppLayer["Application Layer"]
            App1[🚀 App Server 1]
            App2[🚀 App Server 2]
            App3[🚀 App Server 3]
        end

        subgraph Cache["Cache / Lock Layer"]
            Redis[(🔴 Redis<br/>대기열 · 분산 락 · 랭킹)]
        end

        subgraph Messaging["Messaging Layer"]
            Kafka[📨 Kafka Cluster<br/>Broker × 3]
        end

        subgraph DB["Database Layer"]
            Master[(🗄️ MySQL Primary<br/>예약 · 결제 · 잔액 쓰기)]
            Replica[(🗄️ MySQL Replica<br/>콘서트 · 좌석 조회)]
        end
    end

    User --> LB
    LB --> App1 & App2 & App3

    App1 & App2 & App3 --> Redis
    App1 & App2 & App3 --> Kafka
    App1 & App2 & App3 --> Master
    App1 & App2 & App3 -.->|조회| Replica

    Master -.->|복제| Replica
```

### 설계 포인트
- **App 서버 무상태화**: 대기열 토큰·락·세션 정보를 모두 Redis에 두어 어느 인스턴스로 요청이 가도 동일하게 동작
- **분산 락을 Redis로**: 인스턴스가 여러 대여도 좌석·결제 단위 락이 공유되도록 JVM 락 대신 Redis 사용
- **이벤트로 후처리 분리**: 랭킹·알림·외부 전송을 예약/결제 트랜잭션에서 분리해 핵심 API 응답 시간에 영향이 없도록 함 (예약·결제 이벤트의 Kafka 전환은 진행 중)
- **조회 분산(예정)**: 콘서트·좌석 조회는 Replica로 보내 Primary 부하를 줄이는 방향

---

## 3. 이벤트 흐름

```mermaid
graph LR
    subgraph Producer["Kafka Producer (EventPublisher)"]
        Bal[BalanceServiceWithEvents<br/>충전 · 차감]
        Con[ConcertServiceWithEvents<br/>매진]
    end

    subgraph Topics["Kafka Topics"]
        T3[balance-events]
        T4[concert-events]
        T1[reservation-events]
        T2[payment-events]
        T5[queue-events]
    end

    subgraph Consumers["Consumer Groups"]
        Rank[ranking-service<br/>RankingKafkaEventListener]
        Noti[notification-service<br/>NotificationEventListener]
    end

    Bal --> T3
    Con --> T4

    T3 --> Noti
    T4 --> Rank
    T1 -.-> Rank & Noti
    T2 -.-> Rank & Noti
    T5 -.-> Rank & Noti
```

- `EventPublisher`가 `KafkaTemplate`으로 발행 (비동기 `publishEvent` / 동기 `publishEventSync`)
- 현재 Kafka로 실제 발행되는 토픽은 **`balance-events`, `concert-events`** 두 개
- `reservation-events`, `payment-events`, `queue-events`는 **Consumer만 구현된 상태**(점선)이며, 예약 완료 이벤트는 아래 Spring 내부 이벤트로 처리 중

### Spring 내부 이벤트 (트랜잭션 커밋 후 처리)

Kafka와 별개로, 커밋 이후에만 실행되어야 하는 후처리는 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 처리합니다.

| 이벤트 | 리스너 | 처리 |
|---|---|---|
| `ReservationCompletedEvent` | `RankingEventListener` | 예약 속도·인기도 랭킹 갱신 |
| `ConcertSoldOutEvent` | `RankingEventListener` | 매진 속도 랭킹 갱신 |
| `ReservationCompletedEvent` | `DataTransferService` (`@Async`) | 외부 데이터 플랫폼 전송, 실패 시 `FailedDataTransfer` 저장 → `DataRetryService`가 10분 주기 재시도 |

### Consumer Group 분리 이유
- 같은 토픽을 **랭킹**과 **알림**이 각각 전부 받아야 하므로 그룹을 나눔
- 한쪽 Consumer가 느리거나 장애가 나도 다른 쪽 처리에는 영향 없음
