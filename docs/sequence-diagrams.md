# 시퀀스 다이어그램 (Sequence Diagrams)

## 📋 개요

콘서트 예약 서비스의 주요 API 플로우를 시각화한 시퀀스 다이어그램입니다.
각 다이어그램은 사용자와 시스템 간의 상호작용을 시간 순서대로 보여줍니다.

# 시퀀스 다이어그램 분류 요약

## 🔒 대기열 필요 API
- **4. 좌석 예약 요청** - 한정된 자원에 대한 경쟁
- **5. 잔액 충전** - 중요한 금전 거래
- **7. 결제 처리** - 실제 거래 발생

## 🔓 일반 API (토큰 불필요)
- **1. 대기열 토큰 발급** - 대기열 진입용
- **2. 대기열 상태 조회** - 대기열 관리용
- **3. 콘서트 정보 조회** - 단순 정보 확인
- **6. 잔액 조회** - 상태 확인

---

## 🎫 1. 대기열 토큰 발급 플로우

사용자가 서비스 이용을 위해 대기열 토큰을 발급받는 과정

```mermaid
sequenceDiagram
    participant User as 사용자
    participant API as API Gateway
    participant Auth as 인증 서비스
    participant Queue as 대기열 서비스
    participant Redis as Redis
    participant DB as 데이터베이스

    User->>API: POST /api/queue/token
    API->>Auth: 기본 인증 확인
    Auth-->>API: 인증 완료
    
    API->>Queue: 토큰 발급 요청
    Queue->>Redis: 현재 대기열 크기 조회
    Redis-->>Queue: 대기열 정보 반환
    
    Queue->>Queue: UUID 생성 및 대기 순서 계산
    Queue->>Redis: 토큰 정보 저장 (TTL 설정)
    Redis-->>Queue: 저장 완료
    
    Queue->>DB: 토큰 발급 이력 저장
    DB-->>Queue: 저장 완료
    
    Queue-->>API: 토큰 + 대기 정보 반환
    API-->>User: {"token": "uuid", "queuePosition": 150, "estimatedWaitTime": "15분"}
```

---

## 📅 2. 대기열 상태 조회 플로우

사용자가 폴링을 통해 대기열 상태를 확인하는 과정

```mermaid
sequenceDiagram
    participant User as 사용자
    participant API as API Gateway
    participant Queue as 대기열 서비스
    participant Redis as Redis

    loop 폴링 (30초마다)
        User->>API: GET /api/queue/status
        Note over User,API: Headers: Authorization: Bearer {token}
        
        API->>Queue: 토큰 검증 및 상태 조회
        Queue->>Redis: 토큰 유효성 확인
        Redis-->>Queue: 토큰 정보 반환
        
        alt 토큰이 유효한 경우
            Queue->>Redis: 현재 대기 순서 계산
            Redis-->>Queue: 대기 정보 반환
            
            alt 대기 중
                Queue-->>API: {"status": "waiting", "position": 120, "estimatedWaitTime": "12분"}
                API-->>User: 대기 상태 반환
            else 활성화됨
                Queue-->>API: {"status": "active", "activeUntil": "2025-05-29T15:30:00"}
                API-->>User: 서비스 이용 가능
            end
        else 토큰이 무효한 경우
            Queue-->>API: 401 Unauthorized
            API-->>User: 토큰 재발급 필요
        end
    end
```

---

## 🎵 3. 콘서트 정보 조회 플로우 🔓 (일반 API)

예약 가능한 날짜와 좌석 정보를 조회하는 과정 (대기열 토큰 불필요)

```mermaid
sequenceDiagram
    participant User as 사용자
    participant API as API Gateway
    participant Concert as 콘서트 서비스
    participant Cache as 캐시 (Redis)
    participant DB as 데이터베이스

    %% 예약 가능 날짜 조회
    User->>API: GET /api/concerts/available-dates
    Note over User,API: 일반 API - 토큰 불필요
    
    API->>Concert: 예약 가능 날짜 조회
    Concert->>Cache: 캐시된 날짜 정보 확인
    
    alt 캐시 히트
        Cache-->>Concert: 캐시된 데이터 반환
    else 캐시 미스
        Concert->>DB: 예약 가능한 콘서트 조회
        DB-->>Concert: 콘서트 목록 반환
        Concert->>Cache: 결과 캐싱 (TTL: 10분)
    end
    
    Concert-->>API: 날짜 목록 반환
    API-->>User: {"dates": ["2025-06-01", "2025-06-15"]}

    %% 좌석 정보 조회
    User->>API: GET /api/concerts/{concertId}/seats
    Note over User,API: 일반 API - 토큰 불필요
    
    API->>Concert: 좌석 정보 조회
    Concert->>DB: 좌석 상태 실시간 조회
    DB-->>Concert: 좌석 목록 (1-50번)
    
    Concert-->>API: 좌석 정보 반환
    API-->>User: {"seats": [{"number": 1, "status": "available", "price": 50000}]}
```

---

## 🪑 4. 좌석 예약 요청 플로우 🔒 (대기열 필요)

사용자가 좌석을 선택하고 임시 배정을 받는 과정 (대기열 토큰 필요)

```mermaid
sequenceDiagram
    participant User as 사용자
    participant API as API Gateway
    participant Auth as 인증 미들웨어
    participant Reservation as 예약 서비스
    participant Lock as 분산 락 (Redis)
    participant DB as 데이터베이스
    participant Scheduler as 스케줄러

    User->>API: POST /api/reservations
    Note over User,API: {"concertId": 1, "seatNumber": 15}
    
    API->>Auth: 토큰 검증 (활성 상태 확인)
    Auth-->>API: 검증 완료
    
    API->>Reservation: 좌석 예약 요청
    
    %% 동시성 제어를 위한 분산 락
    Reservation->>Lock: 좌석 락 획득 시도
    Note over Reservation,Lock: Key: "seat_lock:{concertId}:{seatNumber}"
    
    alt 락 획득 성공
        Lock-->>Reservation: 락 획득 완료
        
        Reservation->>DB: 좌석 상태 확인 (FOR UPDATE)
        DB-->>Reservation: 좌석 정보 반환
        
        alt 좌석 예약 가능
            Reservation->>DB: 좌석 임시 배정 처리
            Note over Reservation,DB: status: TEMPORARILY_ASSIGNED<br/>assigned_user_id: user-123<br/>assigned_until: now + 5분
            DB-->>Reservation: 업데이트 완료
            
            Reservation->>Scheduler: 5분 후 해제 스케줄 등록
            Scheduler-->>Reservation: 스케줄 등록 완료
            
            Reservation->>Lock: 락 해제
            Lock-->>Reservation: 해제 완료
            
            Reservation-->>API: 예약 성공
            API-->>User: {"reservationId": "res-123", "expiresAt": "2025-05-29T15:35:00", "message": "5분 내에 결제해주세요"}
            
        else 좌석 이미 배정됨
            Reservation->>Lock: 락 해제
            Lock-->>Reservation: 해제 완료
            
            Reservation-->>API: 409 Conflict
            API-->>User: {"error": "이미 다른 사용자가 선택한 좌석입니다"}
        end
        
    else 락 획득 실패
        Lock-->>Reservation: 락 획득 실패
        Reservation-->>API: 409 Conflict
        API-->>User: {"error": "다른 사용자가 처리 중입니다. 잠시 후 재시도해주세요"}
    end
```

---

## 💰 5. 잔액 충전 플로우 🔒 (대기열 필요)

사용자가 결제를 위해 잔액을 충전하는 과정 (대기열 토큰 필요)

```mermaid
sequenceDiagram
    participant User as 사용자
    participant API as API Gateway
    participant Auth as 인증 미들웨어
    participant Balance as 잔액 서비스
    participant DB as 데이터베이스

    User->>API: POST /api/users/{userId}/balance
    Note over User,API: {"amount": 100000}
    
    API->>Auth: 토큰 검증
    Auth-->>API: 검증 완료
    
    API->>Balance: 잔액 충전 요청
    
    Balance->>Balance: 충전 금액 유효성 검증
    Note over Balance: - 최소 금액: 10,000원<br/>- 최대 금액: 1,000,000원<br/>- 양수 값 확인
    
    alt 유효한 충전 금액
        Balance->>DB: 트랜잭션 시작
        Balance->>DB: 사용자 잔액 조회 (FOR UPDATE)
        DB-->>Balance: 현재 잔액 반환
        
        Balance->>DB: 잔액 업데이트
        Note over Balance,DB: balance = current_balance + amount
        
        Balance->>DB: 충전 이력 저장
        Note over Balance,DB: transaction_type: CHARGE<br/>amount: 100000<br/>timestamp: now
        
        Balance->>DB: 트랜잭션 커밋
        DB-->>Balance: 충전 완료
        
        Balance-->>API: 충전 성공
        API-->>User: {"currentBalance": 150000, "chargedAmount": 100000}
        
    else 유효하지 않은 금액
        Balance-->>API: 400 Bad Request
        API-->>User: {"error": "충전 금액이 올바르지 않습니다"}
    end
```

---

## 💰 6. 잔액 조회 플로우 🔓 (일반 API)

사용자가 현재 잔액을 확인하는 과정 (대기열 토큰 불필요)

```mermaid
sequenceDiagram
    participant User as 사용자
    participant API as API Gateway
    participant Balance as 잔액 서비스
    participant Cache as 캐시 (Redis)
    participant DB as 데이터베이스

    User->>API: GET /api/users/{userId}/balance
    Note over User,API: 일반 API - 토큰 불필요
    
    API->>Balance: 잔액 조회 요청
    
    Balance->>Cache: 캐시된 잔액 확인
    Note over Balance,Cache: Key: "user_balance:{userId}"
    
    alt 캐시 히트 (최근 조회)
        Cache-->>Balance: 캐시된 잔액 반환
        Balance-->>API: 잔액 정보 반환
        
    else 캐시 미스 또는 만료
        Balance->>DB: 실시간 잔액 조회
        DB-->>Balance: 잔액 정보 반환
        
        Balance->>Cache: 잔액 정보 캐싱 (TTL: 1분)
        Cache-->>Balance: 캐싱 완료
        
        Balance-->>API: 잔액 정보 반환
    end
    
    API-->>User: {"userId": "user-123", "balance": 150000, "lastUpdated": "2025-05-29T15:25:00"}
```

---

## 💳 7. 결제 처리 플로우 🔒 (대기열 필요)

임시 배정된 좌석에 대해 결제를 완료하는 과정 (대기열 토큰 필요)

```mermaid
sequenceDiagram
    participant User as 사용자
    participant API as API Gateway
    participant Auth as 인증 미들웨어
    participant Payment as 결제 서비스
    participant Balance as 잔액 서비스
    participant Reservation as 예약 서비스
    participant Queue as 대기열 서비스
    participant DB as 데이터베이스

    User->>API: POST /api/payments
    Note over User,API: {"reservationId": "res-123"}

    API->>Auth: 토큰 검증
    Auth-->>API: 검증 완료

    API->>Payment: 결제 요청

    Payment->>DB: 트랜잭션 시작

%% 1. 예약 정보 확인
    Payment->>Reservation: 예약 상태 확인
    Reservation->>DB: 예약 정보 조회 (FOR UPDATE)
    DB-->>Reservation: 예약 정보 반환

    alt 유효한 임시 배정
        Note over Reservation: - 임시 배정 상태 확인<br/>- 만료 시간 확인<br/>- 사용자 일치 확인

    %% 2. 잔액 확인 및 차감
        Payment->>Balance: 잔액 확인 및 차감
        Balance->>DB: 사용자 잔액 조회 (FOR UPDATE)
        DB-->>Balance: 현재 잔액 반환

        alt 잔액 충분
            Balance->>DB: 잔액 차감
            Note over Balance,DB: balance = balance - seat_price

            Balance->>DB: 결제 이력 저장
            DB-->>Balance: 차감 완료

        %% 3. 좌석 확정 배정
            Payment->>Reservation: 좌석 확정 처리
            Reservation->>DB: 좌석 상태 업데이트
            Note over Reservation,DB: status: RESERVED<br/>confirmed_at: now<br/>payment_id: pay-123

        %% 4. 토큰 만료 처리
            Payment->>Queue: 토큰 만료 처리
            Queue->>DB: 토큰 상태 업데이트

            Payment->>DB: 트랜잭션 커밋
            DB-->>Payment: 결제 완료

            Payment-->>API: 결제 성공
            API-->>User: {"paymentId": "pay-123", "seatNumber": 15, "amount": 50000, "status": "CONFIRMED"}

        else 잔액 부족
            Payment->>DB: 트랜잭션 롤백
            Payment-->>API: 400 Bad Request
            API-->>User: {"error": "잔액이 부족합니다", "currentBalance": 30000, "requiredAmount": 50000}
        end

    else 유효하지 않은 예약
        Note over Payment: - 만료된 임시 배정<br/>- 이미 결제 완료<br/>- 권한 없음

        Payment->>DB: 트랜잭션 롤백
        Payment-->>API: 400 Bad Request
        API-->>User: {"error": "유효하지 않은 예약입니다"}
    end
```

---

## ⏰ 8. 임시 배정 자동 해제 플로우

스케줄러가 만료된 임시 배정을 자동으로 해제하는 과정

```mermaid
sequenceDiagram
    participant Scheduler as 스케줄러
    participant Reservation as 예약 서비스
    participant DB as 데이터베이스
    participant Log as 로그 시스템

    loop 30초마다 실행
        Scheduler->>Reservation: 만료된 임시 배정 확인

        Reservation->>DB: 만료된 예약 조회
        Note over Reservation,DB: WHERE status = 'TEMPORARILY_ASSIGNED'<br/>AND assigned_until < NOW()

        DB-->>Reservation: 만료된 예약 목록 반환

        alt 만료된 예약이 있는 경우
            loop 각 만료된 예약에 대해
                Reservation->>DB: 트랜잭션 시작

                Reservation->>DB: 좌석 상태 초기화
                Note over Reservation,DB: status: AVAILABLE<br/>assigned_user_id: NULL<br/>assigned_until: NULL

                Reservation->>DB: 해제 이력 저장
                Note over Reservation,DB: action: AUTO_RELEASE<br/>reason: EXPIRED<br/>timestamp: now

                Reservation->>DB: 트랜잭션 커밋

                Reservation->>Log: 해제 로그 기록
                Log-->>Reservation: 로그 저장 완료
            end

            Reservation-->>Scheduler: 해제 완료 (처리된 개수 반환)

        else 만료된 예약이 없는 경우
            Reservation-->>Scheduler: 처리할 예약 없음
        end
    end
```

---

## 🚨 9. 동시성 충돌 시나리오

여러 사용자가 같은 좌석을 동시에 예약하려는 상황

```mermaid
sequenceDiagram
    participant UserA as 사용자 A
    participant UserB as 사용자 B
    participant API as API Gateway
    participant Lock as 분산 락 (Redis)
    participant DB as 데이터베이스

    Note over UserA,UserB: 두 사용자가 동시에 같은 좌석(15번) 예약 시도

    par 동시 요청
        UserA->>API: POST /api/reservations (seat: 15)
    and
        UserB->>API: POST /api/reservations (seat: 15)
    end

    par 락 획득 경쟁
        API->>Lock: 사용자 A - 좌석 15번 락 요청
    and
        API->>Lock: 사용자 B - 좌석 15번 락 요청
    end

    Lock-->>API: 사용자 A - 락 획득 성공 ✅
    Lock-->>API: 사용자 B - 락 획득 실패 ❌

%% 사용자 A의 성공 플로우
    API->>DB: 사용자 A - 좌석 상태 확인
    DB-->>API: 좌석 예약 가능

    API->>DB: 사용자 A - 임시 배정 처리
    DB-->>API: 배정 완료

    API->>Lock: 사용자 A - 락 해제
    Lock-->>API: 해제 완료

    API-->>UserA: 예약 성공 🎉

%% 사용자 B의 실패 플로우
    API-->>UserB: 409 Conflict - 다른 사용자가 처리 중 ⏳

    Note over UserA,UserB: 결과: 사용자 A는 성공, 사용자 B는 재시도 필요
```

---

## 📊 다이어그램 범례 (Legend)

### 참여자 (Participants)
- **사용자 (User)**: 서비스를 이용하는 고객
- **API Gateway**: REST API 엔드포인트
- **인증 미들웨어 (Auth)**: 토큰 검증 및 대기열 확인
- **각종 서비스**: 비즈니스 로직 처리 계층
- **Redis**: 캐시 및 분산 락
- **데이터베이스 (DB)**: 영속성 데이터 저장
- **스케줄러**: 백그라운드 작업 처리

### 메시지 유형
- **→**: 동기 호출
- **-->>**: 응답 반환
- **->>**: 비동기 호출

### 조건문
- **alt/else**: 조건 분기
- **loop**: 반복 처리
- **par**: 병렬 처리

---

## 📚 관련 문서

- [요구사항 명세서](./requirements.md)
- [API 명세서](./api-spec.md)
- [데이터베이스 ERD](./erd.md)
- [시스템 아키텍처](./architecture.md)