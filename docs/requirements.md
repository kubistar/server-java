# 요구사항 명세서 (Requirements Specification)

## 📋 프로젝트 개요

### 프로젝트명
콘서트 예약 서비스 (Concert Reservation Service)

### 프로젝트 목적
대기열 시스템을 통해 공정하고 안전한 콘서트 좌석 예약 서비스를 제공하여, 동시 접속 상황에서도 안정적인 예약 처리가 가능한 시스템을 구축한다.

### 핵심 가치
- **공정성**: 대기열을 통한 선착순 예약 기회 제공
- **안정성**: 동시성 제어를 통한 좌석 중복 배정 방지
- **확장성**: 다수의 인스턴스 환경에서도 정상 동작
- **신뢰성**: 임시 배정 시스템을 통한 안전한 예약 처리

---

## 🎯 기능적 요구사항 (Functional Requirements)

### FR-001: 사용자 대기열 토큰 관리

#### FR-001-1: 토큰 발급
- **기능**: 서비스 이용을 위한 대기열 토큰 발급
- **입력**: 사용자 UUID
- **출력**: 토큰 정보 (UUID + 대기열 정보)
- **처리 규칙**:
    - 토큰은 사용자 UUID와 대기 순서/잔여 시간 포함
    - 중복 토큰 발급 방지 (동일 사용자)
    - 토큰 유효 시간 관리

#### FR-001-2: 대기열 상태 조회
- **기능**: 현재 대기열 상태 및 순서 확인
- **입력**: 토큰
- **출력**: 대기 순서, 예상 대기 시간, 활성화 상태
- **처리 규칙**:
    - 폴링 방식으로 상태 확인
    - 실시간 대기 순서 업데이트

#### FR-001-3: 토큰 검증
- **기능**: 모든 API 호출 시 토큰 유효성 검증
- **입력**: HTTP Header의 토큰
- **출력**: 검증 결과 (성공/실패)
- **처리 규칙**:
    - 대기열 통과한 사용자만 서비스 이용 가능
    - 만료된 토큰 자동 정리
    - 유효하지 않은 토큰 거부

### FR-002: 콘서트 정보 조회

#### FR-002-1: 예약 가능 날짜 조회
- **기능**: 예약 가능한 콘서트 날짜 목록 제공
- **입력**: 없음 (또는 검색 조건)
- **출력**: 날짜 목록, 콘서트 정보
- **처리 규칙**:
    - 현재 날짜 이후의 콘서트만 조회
    - 매진된 날짜 표시 (선택사항)
    - 페이징 처리 지원

#### FR-002-2: 좌석 정보 조회
- **기능**: 특정 날짜의 예약 가능한 좌석 정보 조회
- **입력**: 콘서트 ID, 날짜
- **출력**: 좌석 번호(1-50), 상태, 가격
- **처리 규칙**:
    - 좌석 상태: 예약가능, 입금전, 결제(예약완료)
    - 실시간 좌석 상태 반영

### FR-003: 좌석 예약 처리

#### FR-003-1: 좌석 예약 요청
- **기능**: 특정 좌석에 대한 예약 요청 처리
- **입력**: 토큰, 콘서트 ID, 날짜, 좌석 번호
- **출력**: 예약 ID, 임시 배정 정보, 결제 마감 시간
- **처리 규칙**:
    - 대기열 통과 사용자만 예약 가능
    - 좌석 중복 예약 방지 (동시성 제어)
    - 임시 배정 시간: 5분 (정책에 따라 조정 가능)

#### FR-003-2: 임시 배정 관리
- **기능**: 결제 전 좌석 임시 점유 관리
- **입력**: 예약 ID
- **출력**: 배정 상태, 남은 시간
- **처리 규칙**:
    - 임시 배정 중 다른 사용자 접근 차단
    - 5분 경과 시 자동 해제
    - 먼저 요청한 사용자에게 우선권 부여
    - 실패한 요청에 대한 적절한 오류 응답(이 선 좌)

#### FR-003-3: 예약 취소
- **기능**: 임시 배정된 좌석 수동 취소
- **입력**: 토큰, 예약 ID
- **출력**: 취소 확인
- **처리 규칙**:
    - 즉시 다른 사용자 예약 가능 상태로 전환

### FR-004: 잔액 관리

#### FR-004-1: 잔액 충전
- **기능**: 결제에 사용할 잔액 충전
- **입력**: 사용자 ID, 충전 금액
- **출력**: 충전 결과, 현재 잔액
- **처리 규칙**:
    - 최소/최대 충전 금액 제한
    - 동시 충전 요청 처리 (동시성 제어)

#### FR-004-2: 잔액 조회
- **기능**: 현재 사용자 잔액 조회
- **입력**: 사용자 ID
- **출력**: 현재 잔액, 사용 가능 금액
- **처리 규칙**:
    - 실시간 잔액 정보 제공
    - 임시 배정으로 인한 예약 금액 표시

### FR-005: 결제 처리

#### FR-005-1: 결제 실행
- **기능**: 임시 배정된 좌석에 대한 결제 처리
- **입력**: 토큰, 예약 ID, 결제 금액
- **출력**: 결제 결과, 결제 ID, 예약 확정 정보
- **처리 규칙**:
    - 임시 배정된 좌석에 대해서만 결제 가능
    - 결제 실패 시 임시 배정 유지
    - 예약 확정 + 잔액 차감
    - 충전된 잔액으로만 결제 처리

#### FR-005-2: 결제 후 처리
- **기능**: 결제 완료 시 좌석 소유권 배정 및 토큰 만료
- **입력**: 결제 ID
- **출력**: 처리 완료 상태
- **처리 규칙**:
    - 좌석 소유권 사용자에게 배정
    - 대기열 토큰 만료 처리
    - 임시 배정에서 확정 예약(결제완료)으로 상태 변경

#### FR-005-3: 결제 실패 처리
- **기능**: 결제 실패 시 롤백 처리
- **입력**: 예약 ID, 실패 사유
- **출력**: 롤백 완료 상태
- **처리 규칙**:
    - 잔액 부족 등의 이유로 결제 실패
    - 임시 배정 시간 내 재시도 가능
    - 시간 초과 시 예약 자동 취소

### FR-006: 대기열 고도화 (선택사항)

#### FR-006-1: 스마트 대기열 관리
- **기능**: 다양한 전략을 통한 효율적 대기열 운영
- **처리 규칙**:
    - 특정 시간 동안 N명에게만 권한 부여
    - 동시 활성 사용자 수를 N으로 제한
    - 시간대별 차등 대기열 운영

#### FR-006-2: 대기열 순서 정확성
- **기능**: 유저간 대기열을 요청 순서대로 정확하게 제공
- **처리 규칙**:
    - 타임스탬프 기반 정확한 순서 관리
    - 분산 환경에서의 순서 일관성 보장
    - 순서 변경 불가 원칙
---

## ⚡ 비기능적 요구사항 (Non-Functional Requirements)

### NFR-001: 다중 인스턴스 지원
- **요구사항**: 다수의 인스턴스로 어플리케이션이 동작하더라도 기능에 문제가 없도록 함
- **구현 방안**:
  - Stateless 애플리케이션 설계
  - 공유 상태는 외부 저장소(Redis, Database) 활용
  - 세션 정보 외부화

### NFR-002: 동시성 이슈 고려
- **요구사항**: 동시성 이슈를 고려하여 구현
- **핵심 포인트**:
  - 동시에 여러 사용자가 예약 요청을 했을 때, 좌석이 중복으로 배정되지 않도록 함
  - 대기열 순서의 정확성 보장
  - 잔액 차감 시 동시성 제어
- **구현 방안**:
  - 데이터베이스 락 메커니즘 활용
  - 분산 락 (Redis) 사용
  - 낙관적/비관적 락 전략

### NFR-003: 대기열 개념 구현
- **요구사항**: 대기열 개념을 고려해 구현
- **핵심 기능**:
  - FIFO(First In First Out) 순서 보장
  - 실시간 대기 상태 제공
  - 공정한 서비스 이용 기회 제공

### NFR-004: 성능 요구사항
- **응답 시간**: API 응답 시간 2초 이내
- **처리량**: 동시 사용자 1,000명 이상 처리
- **대기열 성능**: 10,000명 대기열 관리

### NFR-005: 가용성
- **목표**: 99.9% 서비스 가용성
- **장애 복구**: 5분 이내 자동 복구
- **데이터 백업**: 실시간 백업 및 복구 체계

### NFR-006: 보안
- **토큰 보안**: 토큰 위조/변조 방지
- **API 보안**: 인증되지 않은 접근 차단
- **데이터 암호화**: 중요 정보 암호화 저장

---

## 🧪 테스트 요구사항

### TS-001: 단위 테스트 필수
- **요구사항**: 각 기능 및 제약사항에 대해 단위 테스트를 반드시 하나 이상 작성
- **커버리지**: 비즈니스 로직 80% 이상
- **테스트 범위**:
  - 대기열 토큰 발급 및 검증
  - 좌석 예약 로직
  - 동시성 제어 메커니즘
  - 임시 배정 및 해제
  - 결제 처리 로직
  - 잔액 관리

### TS-002: 통합 테스트
- **API 레벨**: 전체 사용자 시나리오 검증
- **동시성 테스트**: 다중 사용자 동시 접근 시나리오
- **성능 테스트**: 부하 상황에서의 안정성 검증

### TS-003: 예외 상황 테스트
- **동시 예약**: 같은 좌석에 대한 동시 예약 요청
- **시간 초과**: 임시 배정 시간 초과 상황
- **잔액 부족**: 결제 시 잔액 부족 상황
- **토큰 만료**: 만료된 토큰으로 접근 시도

---


## 🚫 제약사항 (Constraints)

### 비즈니스 제약사항

#### C-001: 좌석 관리
- 좌석 번호는 1-50번으로 고정
- 하나의 좌석은 하나의 예약자만 배정 가능
- 임시 배정 시간은 5분 고정 (정책에 따라 조정 가능)

#### C-002: 결제 시스템
- 사전 충전된 잔액만 사용 가능
- 외부 결제 시스템 연동 불포함
- 결제 완료 후 환불 불가

#### C-003: 대기열 시스템
- 대기열 토큰 없이는 서비스 이용 불가
- 결제 완료 시 토큰 자동 만료
- 중복 토큰 발급 방지
- 대기열 순서는 요청 순서대로 정확하게 제공

### 기술적 제약사항

#### C-004: 구현 사항
- 5가지 필수 API 구현 (토큰, 날짜/좌석, 예약, 잔액, 결제)
- 다수 인스턴스 환경 지원 필수
- 동시성 이슈 해결 필수
- 대기열 개념 적용 필수

#### C-005: 품질 기준
- 단위 테스트 필수 작성
- 코드 품질 및 가독성 확보
- 문서화 및 주석 작성

---

## 📊 우선순위 (Priority)

### 필수 구현 (High Priority)
1. **FR-001**: 유저 대기열 토큰 기능 (주요)
2. **FR-003**: 좌석 예약 요청 API (주요)
3. **FR-005**: 결제 API (주요)
4. **NFR-001**: 다중 인스턴스 지원
5. **NFR-002**: 동시성 이슈 고려
6. **TS-001**: 단위 테스트 작성

### 기본 구현 (Medium Priority)
1. **FR-002**: 예약 가능 날짜 / 좌석 API (기본)
2. **FR-004**: 잔액 충전 / 조회 API (기본)
3. **TS-002**: 통합 테스트

### 심화 구현 (Low Priority)
1. **FR-006**: 대기열 고도화 (심화)
2. 실시간 알림 기능
3. 모니터링 및 로깅 고도화

---

## 🔑 핵심 포인트 (Key Points)

### 1. 유저간 대기열을 요청 순서대로 정확하게 제공할 방법
- **타임스탬프 기반 순서 관리**
- **분산 환경에서의 시간 동기화**
- **FIFO 큐 자료구조 활용**
- **순서 변경 불가 원칙**

### 2. 동시에 여러 사용자가 예약 요청을 했을 때, 좌석이 중복으로 배정되지 않도록 하는 방법
- **데이터베이스 락 메커니즘**
  - 비관적 락: SELECT FOR UPDATE
  - 낙관적 락: 버전 관리
- **분산 락 (Redis)**
  - 애플리케이션 레벨 동시성 제어
- **원자적 연산**
  - CAS(Compare And Swap) 활용
- **트랜잭션 격리 수준**
  - SERIALIZABLE 또는 REPEATABLE READ

---

# API 명세서 (API Specification)

## 📋 개요

콘서트 예약 서비스의 REST API 명세서입니다. API는 두 가지 유형으로 분류됩니다.

### 기본 정보
- **Base URL**: `http://localhost:8080`
- **Content-Type**: `application/json`

### API 분류

#### 🔒 대기열 필요 API (Queue Required)
실제 예약 및 결제 등 중요한 작업을 수행하는 API
```http
Authorization: Bearer {queue-token}
Content-Type: application/json
```

#### 🔓 일반 API (Public Access)
정보 조회 등 일반적인 접근이 가능한 API
```http
Content-Type: application/json
```

---

## 🎫 1. 대기열 토큰 관리 🔓

### 1-1. 토큰 발급

**대기열 진입을 위한 토큰을 발급받습니다.**

```http
POST /api/queue/token
```

#### Request
```json
{
  "userId": "user-123"
}
```

#### Response (201 Created)
```json
{
  "success": true,
  "data": {
    "token": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "user-123",
    "queuePosition": 150,
    "estimatedWaitTimeMinutes": 15,
    "status": "WAITING",
    "issuedAt": "2025-05-29T15:20:00Z",
    "expiresAt": "2025-05-29T16:20:00Z"
  },
  "message": "대기열 토큰이 발급되었습니다."
}
```

#### Error Responses
```json
// 400 Bad Request
{
  "success": false,
  "error": {
    "code": "INVALID_USER_ID",
    "message": "유효하지 않은 사용자 ID입니다."
  }
}

// 409 Conflict - 이미 발급된 토큰 존재
{
  "success": false,
  "error": {
    "code": "TOKEN_ALREADY_EXISTS",
    "message": "이미 발급된 토큰이 존재합니다."
  }
}
```

### 1-2. 대기열 상태 조회

**현재 대기열 상태를 조회합니다.**

```http
GET /api/queue/status
```

#### Request Headers
```http
Authorization: Bearer {token}
```

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "token": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "user-123",
    "status": "WAITING",
    "queuePosition": 120,
    "estimatedWaitTimeMinutes": 12,
    "totalInQueue": 1500,
    "activeUsers": 100,
    "maxActiveUsers": 200
  }
}
```

#### Status Values
| 상태 | 설명 |
|------|------|
| WAITING | 대기 중 |
| ACTIVE | 서비스 이용 가능 |
| EXPIRED | 토큰 만료 |

---

## 🎵 2. 콘서트 정보 조회 🔓

### 2-1. 예약 가능 날짜 조회

**예약 가능한 콘서트 날짜 목록을 조회합니다.**

```http
GET /api/concerts/available-dates
```

#### Query Parameters
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| page | number | N | 0 | 페이지 번호 |
| size | number | N | 20 | 페이지 크기 |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "concerts": [
      {
        "concertId": 1,
        "title": "2025 Spring Concert",
        "artist": "IU",
        "venue": "올림픽공원 체조경기장",
        "date": "2025-06-01",
        "time": "19:00",
        "totalSeats": 50,
        "availableSeats": 35,
        "priceRange": {
          "min": 50000,
          "max": 150000
        },
        "status": "AVAILABLE"
      }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 25,
      "totalPages": 2
    }
  }
}
```

### 2-2. 좌석 정보 조회

**특정 콘서트의 좌석 정보를 조회합니다.**

```http
GET /api/concerts/{concertId}/seats
```

#### Path Parameters
| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| concertId | number | Y | 콘서트 ID |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "concertId": 1,
    "concertTitle": "2025 Spring Concert",
    "concertDate": "2025-06-01",
    "concertTime": "19:00",
    "venue": "올림픽공원 체조경기장",
    "seats": [
      {
        "seatNumber": 1,
        "status": "AVAILABLE",
        "price": 50000,
        "section": "A",
        "row": 1
      },
      {
        "seatNumber": 2,
        "status": "TEMPORARILY_ASSIGNED",
        "price": 50000,
        "section": "A",
        "row": 1,
        "assignedUntil": "2025-05-29T15:35:00Z"
      },
      {
        "seatNumber": 3,
        "status": "RESERVED",
        "price": 50000,
        "section": "A",
        "row": 1
      }
    ],
    "summary": {
      "totalSeats": 50,
      "availableSeats": 35,
      "temporarilyAssignedSeats": 10,
      "reservedSeats": 5
    }
  }
}
```

#### Seat Status Values
| 상태 | 설명 |
|------|------|
| AVAILABLE | 예약 가능 |
| TEMPORARILY_ASSIGNED | 임시 배정 중 (5분간) |
| RESERVED | 예약 완료 |

---

## 🪑 3. 좌석 예약 🔒

### 3-1. 좌석 예약 요청

**선택한 좌석을 임시 배정받습니다.**

```http
POST /api/reservations
```

#### Request Headers
```http
Authorization: Bearer {queue-token}
Content-Type: application/json
```

#### Request Body
```json
{
  "concertId": 1,
  "seatNumber": 15,
  "userId": "user-123"
}
```

#### Response (201 Created)
```json
{
  "success": true,
  "data": {
    "reservationId": "res-123456",
    "concertId": 1,
    "seatNumber": 15,
    "userId": "user-123",
    "status": "TEMPORARILY_ASSIGNED",
    "price": 50000,
    "assignedAt": "2025-05-29T15:30:00Z",
    "expiresAt": "2025-05-29T15:35:00Z",
    "remainingTimeMinutes": 5
  },
  "message": "좌석이 임시 배정되었습니다. 5분 내에 결제를 완료해주세요."
}
```

#### Error Responses
```json
// 409 Conflict - 이미 예약된 좌석
{
  "success": false,
  "error": {
    "code": "SEAT_NOT_AVAILABLE",
    "message": "선택한 좌석은 이미 다른 사용자가 예약했습니다.",
    "details": {
      "seatNumber": 15,
      "currentStatus": "RESERVED"
    }
  }
}

// 409 Conflict - 동시 예약 시도
{
  "success": false,
  "error": {
    "code": "CONCURRENT_RESERVATION_CONFLICT",
    "message": "다른 사용자가 같은 좌석을 처리 중입니다. 잠시 후 재시도해주세요."
  }
}

// 403 Forbidden - 대기열 토큰 비활성
{
  "success": false,
  "error": {
    "code": "QUEUE_TOKEN_NOT_ACTIVE",
    "message": "대기열에서 순서를 기다려주세요.",
    "details": {
      "queuePosition": 50,
      "estimatedWaitTimeMinutes": 5
    }
  }
}
```

### 3-2. 예약 상태 조회

**현재 예약 상태를 조회합니다.**

```http
GET /api/reservations/{reservationId}
```

#### Request Headers
```http
Authorization: Bearer {queue-token}
```

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "reservationId": "res-123456",
    "concertId": 1,
    "seatNumber": 15,
    "userId": "user-123",
    "status": "TEMPORARILY_ASSIGNED",
    "price": 50000,
    "assignedAt": "2025-05-29T15:30:00Z",
    "expiresAt": "2025-05-29T15:35:00Z",
    "remainingTimeSeconds": 180
  }
}
```

---

## 💰 4. 잔액 관리

### 4-1. 잔액 충전 🔒

**사용자 계정에 잔액을 충전합니다.**

```http
POST /api/users/{userId}/balance
```

#### Path Parameters
| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| userId | string | Y | 사용자 식별자 |

#### Request Headers
```http
Authorization: Bearer {queue-token}
Content-Type: application/json
```

#### Request Body
```json
{
  "amount": 100000
}
```

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "userId": "user-123",
    "transactionId": "txn-789",
    "amount": 100000,
    "previousBalance": 50000,
    "currentBalance": 150000,
    "chargedAt": "2025-05-29T15:25:00Z"
  },
  "message": "잔액이 성공적으로 충전되었습니다."
}
```

#### Validation Rules
- **최소 충전 금액**: 10,000원
- **최대 충전 금액**: 1,000,000원
- **충전 단위**: 1,000원 단위

#### Error Responses
```json
// 400 Bad Request - 잘못된 충전 금액
{
  "success": false,
  "error": {
    "code": "INVALID_AMOUNT",
    "message": "충전 금액은 10,000원 이상 1,000,000원 이하여야 합니다.",
    "details": {
      "minAmount": 10000,
      "maxAmount": 1000000,
      "requestedAmount": 5000
    }
  }
}
```

### 4-2. 잔액 조회 🔓

**현재 사용자의 잔액을 조회합니다.**

```http
GET /api/users/{userId}/balance
```

#### Path Parameters
| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| userId | string | Y | 사용자 식별자 |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "userId": "user-123",
    "currentBalance": 150000,
    "pendingAmount": 50000,
    "availableBalance": 100000,
    "lastTransactionAt": "2025-05-29T15:25:00Z"
  }
}
```

#### Response Fields
| 필드 | 타입 | 설명 |
|------|------|------|
| currentBalance | number | 현재 총 잔액 |
| pendingAmount | number | 임시 배정으로 홀드된 금액 |
| availableBalance | number | 실제 사용 가능한 금액 |
| lastTransactionAt | string | 마지막 거래 시간 |

---

## 💳 5. 결제 처리 🔒

### 5-1. 결제 실행

**임시 배정된 좌석에 대해 결제를 진행합니다.**

```http
POST /api/payments
```

#### Request Headers
```http
Authorization: Bearer {queue-token}
Content-Type: application/json
```

#### Request Body
```json
{
  "reservationId": "res-123456",
  "userId": "user-123"
}
```

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "paymentId": "pay-456789",
    "reservationId": "res-123456",
    "userId": "user-123",
    "concertId": 1,
    "seatNumber": 15,
    "amount": 50000,
    "status": "COMPLETED",
    "paidAt": "2025-05-29T15:32:00Z",
    "ticket": {
      "ticketId": "ticket-789",
      "concertTitle": "2025 Spring Concert",
      "artist": "IU",
      "venue": "올림픽공원 체조경기장",
      "concertDate": "2025-06-01",
      "concertTime": "19:00",
      "seatNumber": 15,
      "price": 50000
    },
    "balanceAfterPayment": 100000
  },
  "message": "결제가 완료되었습니다. 대기열 토큰이 만료됩니다."
}
```

#### Error Responses
```json
// 400 Bad Request - 잔액 부족
{
  "success": false,
  "error": {
    "code": "INSUFFICIENT_BALANCE",
    "message": "잔액이 부족합니다.",
    "details": {
      "currentBalance": 30000,
      "requiredAmount": 50000,
      "shortfallAmount": 20000
    }
  }
}

// 400 Bad Request - 만료된 임시 배정
{
  "success": false,
  "error": {
    "code": "RESERVATION_EXPIRED",
    "message": "예약 시간이 만료되었습니다. 다시 예약해주세요.",
    "details": {
      "reservationId": "res-123456",
      "expiredAt": "2025-05-29T15:35:00Z"
    }
  }
}

// 409 Conflict - 이미 결제된 예약
{
  "success": false,
  "error": {
    "code": "ALREADY_PAID",
    "message": "이미 결제가 완료된 예약입니다.",
    "details": {
      "paymentId": "pay-456789",
      "paidAt": "2025-05-29T15:32:00Z"
    }
  }
}
```

### 5-2. 결제 내역 조회 🔓

**결제 상세 정보를 조회합니다.**

```http
GET /api/payments/{paymentId}
```

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "paymentId": "pay-456789",
    "reservationId": "res-123456",
    "userId": "user-123",
    "amount": 50000,
    "status": "COMPLETED",
    "paidAt": "2025-05-29T15:32:00Z",
    "concert": {
      "concertId": 1,
      "title": "2025 Spring Concert",
      "artist": "IU",
      "date": "2025-06-01",
      "time": "19:00",
      "venue": "올림픽공원 체조경기장",
      "seatNumber": 15
    }
  }
}
```

---

## 🚨 공통 에러 응답

### 인증 관련 에러

```json
// 401 Unauthorized - 토큰 없음
{
  "success": false,
  "error": {
    "code": "MISSING_TOKEN",
    "message": "대기열 토큰이 필요합니다."
  }
}

// 401 Unauthorized - 유효하지 않은 토큰
{
  "success": false,
  "error": {
    "code": "INVALID_TOKEN",
    "message": "유효하지 않은 토큰입니다."
  }
}

// 403 Forbidden - 대기열 미통과
{
  "success": false,
  "error": {
    "code": "QUEUE_TOKEN_NOT_ACTIVE",
    "message": "대기열에서 순서를 기다려주세요.",
    "details": {
      "queuePosition": 50,
      "estimatedWaitTimeMinutes": 5
    }
  }
}
```

### 서버 에러

```json
// 500 Internal Server Error
{
  "success": false,
  "error": {
    "code": "INTERNAL_SERVER_ERROR",
    "message": "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
    "timestamp": "2025-05-29T15:35:00Z"
  }
}

// 503 Service Unavailable
{
  "success": false,
  "error": {
    "code": "SERVICE_TEMPORARILY_UNAVAILABLE",
    "message": "서비스가 일시적으로 이용 불가능합니다.",
    "retryAfterSeconds": 30
  }
}
```

---

## 📊 API 분류 요약

### 🔒 대기열 필요 API (Critical Operations)
- **POST /api/reservations** - 좌석 예약
- **POST /api/users/{userId}/balance** - 잔액 충전
- **POST /api/payments** - 결제 실행
- **GET /api/reservations/{reservationId}** - 예약 상태 조회

### 🔓 일반 API (Information Access)
- **POST /api/queue/token** - 토큰 발급
- **GET /api/queue/status** - 대기열 상태 조회
- **GET /api/concerts/available-dates** - 콘서트 날짜 조회
- **GET /api/concerts/{concertId}/seats** - 좌석 정보 조회
- **GET /api/users/{userId}/balance** - 잔액 조회
- **GET /api/payments/{paymentId}** - 결제 내역 조회

---

## 📊 HTTP 상태 코드

| 상태 코드 | 설명 | 사용 상황 |
|-----------|------|-----------|
| 200 | OK | 조회 성공 |
| 201 | Created | 생성 성공 (토큰 발급, 예약) |
| 400 | Bad Request | 잘못된 요청 파라미터 |
| 401 | Unauthorized | 인증 실패 |
| 403 | Forbidden | 권한 없음 (대기열 미통과) |
| 404 | Not Found | 리소스 없음 |
| 409 | Conflict | 동시성 충돌, 중복 요청 |
| 422 | Unprocessable Entity | 비즈니스 로직 위반 |
| 429 | Too Many Requests | 요청 횟수 제한 초과 |
| 500 | Internal Server Error | 서버 오류 |
| 503 | Service Unavailable | 서비스 일시 중단 |

---

## 🔧 API 테스트 예시

```bash
# 1. 토큰 발급
curl -X POST http://localhost:8080/api/queue/token \
  -H "Content-Type: application/json" \
  -d '{"userId": "user-123"}'

# 2. 대기열 상태 확인
curl -X GET http://localhost:8080/api/queue/status \
  -H "Authorization: Bearer {your-token}"

# 3. 콘서트 날짜 조회 (토큰 불필요)
curl -X GET http://localhost:8080/api/concerts/available-dates

# 4. 좌석 조회 (토큰 불필요)
curl -X GET http://localhost:8080/api/concerts/1/seats

# 5. 잔액 조회 (토큰 불필요)
curl -X GET http://localhost:8080/api/users/user-123/balance

# 6. 잔액 충전 (토큰 필요)
curl -X POST http://localhost:8080/api/users/user-123/balance \
  -H "Authorization: Bearer {your-token}" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100000}'

# 7. 좌석 예약 (토큰 필요)
curl -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer {your-token}" \
  -H "Content-Type: application/json" \
  -d '{"concertId": 1, "seatNumber": 15, "userId": "user-123"}'

# 8. 결제 (토큰 필요)
curl -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer {your-token}" \
  -H "Content-Type: application/json" \
  -d '{"reservationId": "res-123456", "userId": "user-123"}'
```

---

## 📚 관련 문서

- [시퀀스 다이어그램](./sequence-diagrams.md)
- [데이터베이스 ERD](./erd.md)
- [API 명세서](./api-spec.md)
- [시스템 아키텍처](./architecture.md)