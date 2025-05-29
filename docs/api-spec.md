# API 명세서 (API Specification)

## 📋 개요

콘서트 예약 서비스의 REST API 명세서입니다. 

### 기본 정보
- **Base URL**: `http://localhost:8080`
- **Content-Type**: `application/json`
- **Database**: MySQL + Redis

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

#### Request Fields
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| userId | string | Y | 사용자 식별자 (VARCHAR(50)) |

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

#### Response Fields
| 필드 | 타입 | 설명 |
|------|------|------|
| token | string | UUID 형태의 대기열 토큰 (Redis 키) |
| userId | string | 사용자 식별자 |
| queuePosition | integer | 현재 대기 순서 |
| estimatedWaitTimeMinutes | integer | 예상 대기 시간(분) |
| status | string | 대기열 상태 (WAITING, ACTIVE, EXPIRED) |
| issuedAt | string | 토큰 발급 시간 (ISO 8601) |
| expiresAt | string | 토큰 만료 시간 (ISO 8601) |

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

// 409 Conflict
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
| page | integer | N | 0 | 페이지 번호 |
| size | integer | N | 20 | 페이지 크기 |

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
        "concertDate": "2025-06-01",
        "concertTime": "19:00:00",
        "totalSeats": 50,
        "availableSeats": 35,
        "minPrice": 50000,
        "maxPrice": 150000
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

#### Response Fields (concerts 배열)
| 필드 | 타입 | 설명 | ERD 매핑 |
|------|------|------|----------|
| concertId | integer | 콘서트 ID | concerts.concert_id (BIGINT) |
| title | string | 콘서트 제목 | concerts.title (VARCHAR(200)) |
| artist | string | 아티스트명 | concerts.artist (VARCHAR(100)) |
| venue | string | 공연장 | concerts.venue (VARCHAR(200)) |
| concertDate | string | 공연 날짜 (YYYY-MM-DD) | concerts.concert_date (DATE) |
| concertTime | string | 공연 시간 (HH:mm:ss) | concerts.concert_time (TIME) |
| totalSeats | integer | 총 좌석 수 | concerts.total_seats (INT) |
| availableSeats | integer | 예약 가능한 좌석 수 | COUNT(seats WHERE status='AVAILABLE') |
| minPrice | integer | 최저 가격 | MIN(seats.price) |
| maxPrice | integer | 최고 가격 | MAX(seats.price) |

### 2-2. 좌석 정보 조회

**특정 콘서트의 좌석 정보를 조회합니다.**

```http
GET /api/concerts/{concertId}/seats
```

#### Path Parameters
| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| concertId | integer | Y | 콘서트 ID (concerts.concert_id) |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "concertId": 1,
    "concertTitle": "2025 Spring Concert",
    "concertDate": "2025-06-01",
    "concertTime": "19:00:00",
    "venue": "올림픽공원 체조경기장",
    "seats": [
      {
        "seatId": 1,
        "seatNumber": 1,
        "status": "AVAILABLE",
        "price": 50000
      },
      {
        "seatId": 2,
        "seatNumber": 2,
        "status": "TEMPORARILY_ASSIGNED",
        "price": 50000,
        "assignedUntil": "2025-05-29T15:35:00Z"
      },
      {
        "seatId": 3,
        "seatNumber": 3,
        "status": "RESERVED",
        "price": 50000,
        "reservedAt": "2025-05-29T14:30:00Z"
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

#### Response Fields (seats 배열)
| 필드 | 타입 | 설명 | ERD 매핑 |
|------|------|------|----------|
| seatId | integer | 좌석 ID | seats.seat_id (BIGINT) |
| seatNumber | integer | 좌석 번호 (1-50) | seats.seat_number (INT) |
| status | string | 좌석 상태 | seats.status (ENUM) |
| price | integer | 좌석 가격 | seats.price (DECIMAL→INT) |
| assignedUntil | string | 임시 배정 만료 시간 | seats.assigned_until (TIMESTAMP) |
| reservedAt | string | 예약 확정 시간 | seats.reserved_at (TIMESTAMP) |

#### Seat Status Values
| 상태 | 설명 | ERD 매핑 |
|------|------|----------|
| AVAILABLE | 예약 가능 | seats.status = 'AVAILABLE' |
| TEMPORARILY_ASSIGNED | 임시 배정 중 (5분간) | seats.status = 'TEMPORARILY_ASSIGNED' |
| RESERVED | 예약 완료 | seats.status = 'RESERVED' |

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

#### Request Fields
| 필드 | 타입 | 필수 | 설명 | ERD 매핑 |
|------|------|------|------|----------|
| concertId | integer | Y | 콘서트 ID | concerts.concert_id |
| seatNumber | integer | Y | 좌석 번호 (1-50) | seats.seat_number |
| userId | string | Y | 사용자 식별자 | users.user_id |

#### Response (201 Created)
```json
{
  "success": true,
  "data": {
    "reservationId": "550e8400-e29b-41d4-a716-446655440001",
    "seatId": 15,
    "concertId": 1,
    "seatNumber": 15,
    "userId": "user-123",
    "status": "TEMPORARILY_ASSIGNED",
    "price": 50000,
    "createdAt": "2025-05-29T15:30:00Z",
    "expiresAt": "2025-05-29T15:35:00Z",
    "remainingTimeSeconds": 300
  },
  "message": "좌석이 임시 배정되었습니다. 5분 내에 결제를 완료해주세요."
}
```

#### Response Fields
| 필드 | 타입 | 설명 | ERD 매핑 |
|------|------|------|----------|
| reservationId | string | 예약 ID (UUID) | reservations.reservation_id (VARCHAR(36)) |
| seatId | integer | 좌석 ID | reservations.seat_id (BIGINT) |
| concertId | integer | 콘서트 ID | reservations.concert_id (BIGINT) |
| seatNumber | integer | 좌석 번호 | seats.seat_number |
| userId | string | 사용자 ID | reservations.user_id (VARCHAR(50)) |
| status | string | 예약 상태 | reservations.status (ENUM) |
| price | integer | 예약 가격 | reservations.price (DECIMAL→INT) |
| createdAt | string | 예약 생성 시간 | reservations.created_at (TIMESTAMP) |
| expiresAt | string | 만료 시간 | reservations.expires_at (TIMESTAMP) |
| remainingTimeSeconds | integer | 남은 시간 (초) | 계산값 |

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

#### Path Parameters
| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| reservationId | string | Y | 예약 ID (UUID) |

#### Request Headers
```http
Authorization: Bearer {queue-token}
```

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "reservationId": "550e8400-e29b-41d4-a716-446655440001",
    "userId": "user-123",
    "concertId": 1,
    "seatId": 15,
    "seatNumber": 15,
    "status": "TEMPORARILY_ASSIGNED",
    "price": 50000,
    "createdAt": "2025-05-29T15:30:00Z",
    "expiresAt": "2025-05-29T15:35:00Z",
    "confirmedAt": null,
    "remainingTimeSeconds": 180
  }
}
```

#### Reservation Status Values
| 상태 | 설명 | ERD 매핑 |
|------|------|----------|
| TEMPORARILY_ASSIGNED | 임시 배정 중 | reservations.status = 'TEMPORARILY_ASSIGNED' |
| CONFIRMED | 결제 완료로 확정 | reservations.status = 'CONFIRMED' |
| CANCELLED | 사용자 취소 | reservations.status = 'CANCELLED' |
| EXPIRED | 시간 만료로 자동 취소 | reservations.status = 'EXPIRED' |

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
| userId | string | Y | 사용자 식별자 (users.user_id) |

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

#### Request Fields
| 필드 | 타입 | 필수 | 설명 | ERD 매핑 |
|------|------|------|------|----------|
| amount | integer | Y | 충전 금액 (원) | balance_transactions.amount (DECIMAL→INT) |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "userId": "user-123",
    "transactionId": "550e8400-e29b-41d4-a716-446655440002",
    "transactionType": "CHARGE",
    "amount": 100000,
    "previousBalance": 50000,
    "currentBalance": 150000,
    "chargedAt": "2025-05-29T15:25:00Z"
  },
  "message": "잔액이 성공적으로 충전되었습니다."
}
```

#### Response Fields
| 필드 | 타입 | 설명 | ERD 매핑 |
|------|------|------|----------|
| userId | string | 사용자 ID | users.user_id |
| transactionId | string | 거래 ID (UUID) | balance_transactions.transaction_id |
| transactionType | string | 거래 유형 | balance_transactions.transaction_type |
| amount | integer | 거래 금액 | balance_transactions.amount |
| previousBalance | integer | 이전 잔액 | 계산값 |
| currentBalance | integer | 현재 잔액 | users.balance, balance_transactions.balance_after |
| chargedAt | string | 충전 시간 | balance_transactions.created_at |

#### Validation Rules
- **최소 충전 금액**: 10,000원
- **최대 충전 금액**: 1,000,000원
- **충전 단위**: 1,000원 단위

#### Transaction Type Values
| 타입 | 설명 | ERD 매핑 |
|------|------|----------|
| CHARGE | 잔액 충전 | balance_transactions.transaction_type = 'CHARGE' |
| PAYMENT | 결제로 인한 차감 | balance_transactions.transaction_type = 'PAYMENT' |
| REFUND | 환불 | balance_transactions.transaction_type = 'REFUND' |

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
    "lastTransactionAt": "2025-05-29T15:25:00Z"
  }
}
```

#### Response Fields
| 필드 | 타입 | 설명 | ERD 매핑 |
|------|------|------|----------|
| userId | string | 사용자 ID | users.user_id |
| currentBalance | integer | 현재 잔액 | users.balance (DECIMAL→INT) |
| lastTransactionAt | string | 마지막 거래 시간 | MAX(balance_transactions.created_at) |

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
  "reservationId": "550e8400-e29b-41d4-a716-446655440001",
  "userId": "user-123"
}
```

#### Request Fields
| 필드 | 타입 | 필수 | 설명 | ERD 매핑 |
|------|------|------|------|----------|
| reservationId | string | Y | 예약 ID (UUID) | payments.reservation_id |
| userId | string | Y | 사용자 ID | payments.user_id |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "paymentId": "550e8400-e29b-41d4-a716-446655440003",
    "reservationId": "550e8400-e29b-41d4-a716-446655440001",
    "userId": "user-123",
    "concertId": 1,
    "seatNumber": 15,
    "amount": 50000,
    "paymentMethod": "BALANCE",
    "status": "COMPLETED",
    "paidAt": "2025-05-29T15:32:00Z",
    "ticket": {
      "ticketId": "TICKET-550e8400-e29b-41d4-a716-446655440003",
      "concertTitle": "2025 Spring Concert",
      "artist": "IU",
      "venue": "올림픽공원 체조경기장",
      "concertDate": "2025-06-01",
      "concertTime": "19:00:00",
      "seatNumber": 15,
      "price": 50000
    },
    "balanceAfterPayment": 100000
  },
  "message": "결제가 완료되었습니다. 대기열 토큰이 만료됩니다."
}
```

#### Response Fields
| 필드 | 타입 | 설명 | ERD 매핑 |
|------|------|------|----------|
| paymentId | string | 결제 ID (UUID) | payments.payment_id |
| reservationId | string | 예약 ID | payments.reservation_id |
| userId | string | 사용자 ID | payments.user_id |
| amount | integer | 결제 금액 | payments.amount (DECIMAL→INT) |
| paymentMethod | string | 결제 수단 | payments.payment_method |
| status | string | 결제 상태 | payments.status |
| paidAt | string | 결제 시간 | payments.created_at |
| balanceAfterPayment | integer | 결제 후 잔액 | users.balance |

#### Payment Status Values
| 상태 | 설명 | ERD 매핑 |
|------|------|----------|
| COMPLETED | 결제 완료 | payments.status = 'COMPLETED' |
| FAILED | 결제 실패 | payments.status = 'FAILED' |
| CANCELLED | 결제 취소 | payments.status = 'CANCELLED' |

#### Payment Method Values
| 수단 | 설명 | ERD 매핑 |
|------|------|----------|
| BALANCE | 충전된 잔액 | payments.payment_method = 'BALANCE' |

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
      "reservationId": "550e8400-e29b-41d4-a716-446655440001",
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
      "paymentId": "550e8400-e29b-41d4-a716-446655440003",
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

#### Path Parameters
| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| paymentId | string | Y | 결제 ID (UUID) |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "paymentId": "550e8400-e29b-41d4-a716-446655440003",
    "reservationId": "550e8400-e29b-41d4-a716-446655440001",
    "userId": "user-123",
    "amount": 50000,
    "paymentMethod": "BALANCE",
    "status": "COMPLETED",
    "paidAt": "2025-05-29T15:32:00Z",
    "concert": {
      "concertId": 1,
      "title": "2025 Spring Concert",
      "artist": "IU",
      "concertDate": "2025-06-01",
      "concertTime": "19:00:00",
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

### 데이터 검증 에러

```json
// 400 Bad Request - 잘못된 데이터 타입
{
  "success": false,
  "error": {
    "code": "INVALID_DATA_TYPE",
    "message": "concertId는 정수여야 합니다.",
    "details": {
      "field": "concertId",
      "expectedType": "integer",
      "receivedValue": "abc"
    }
  }
}

// 404 Not Found - 리소스 없음
{
  "success": false,
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "해당 콘서트를 찾을 수 없습니다.",
    "details": {
      "concertId": 999
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

## 📊 데이터 타입 매핑

### MySQL → JSON 응답 변환

| MySQL 타입 | JSON 타입 | 변환 규칙 | 예시 |
|-------------|-----------|-----------|------|
| BIGINT | integer | 그대로 | `123` |
| VARCHAR(50) | string | 그대로 | `"user-123"` |
| VARCHAR(36) | string | UUID 형태 | `"550e8400-e29b-41d4-a716-446655440000"` |
| DECIMAL(10,2) | integer | 소수점 제거 (원 단위) | `50000` (500.00 → 50000) |
| DECIMAL(15,2) | integer | 소수점 제거 (원 단위) | `150000` |
| TIMESTAMP | string | ISO 8601 형태 | `"2025-05-29T15:30:00Z"` |
| DATE | string | YYYY-MM-DD 형태 | `"2025-06-01"` |
| TIME | string | HH:mm:ss 형태 | `"19:00:00"` |
| ENUM | string | 문자열 그대로 | `"AVAILABLE"` |

### Redis → JSON 응답 변환

| Redis 타입 | JSON 타입 | 변환 규칙 | 예시 |
|-------------|-----------|-----------|------|
| String (JSON) | object | JSON 파싱 | `{"position": 150, "status": "WAITING"}` |
| Sorted Set Score | integer | 점수를 정수로 | `150` (대기 순서) |
| Set Member | string | 문자열 그대로 | `"user-123"` |
| TTL | integer | 초 단위 | `300` (5분 = 300초) |

---

## 📊 API 분류 요약

### 🔒 대기열 필요 API (Critical Operations)
- **POST /api/reservations** - 좌석 예약
- **GET /api/reservations/{reservationId}** - 예약 상태 조회
- **POST /api/users/{userId}/balance** - 잔액 충전
- **POST /api/payments** - 결제 실행

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
| 400 | Bad Request | 잘못된 요청 파라미터, 데이터 타입 오류 |
| 401 | Unauthorized | 인증 실패, 토큰 없음/무효 |
| 403 | Forbidden | 권한 없음 (대기열 미통과) |
| 404 | Not Found | 리소스 없음 (콘서트, 예약, 결제 등) |
| 409 | Conflict | 동시성 충돌, 중복 요청, 좌석 중복 예약 |
| 422 | Unprocessable Entity | 비즈니스 로직 위반 |
| 429 | Too Many Requests | 요청 횟수 제한 초과 |
| 500 | Internal Server Error | 서버 오류 |
| 503 | Service Unavailable | 서비스 일시 중단 |

---

## 🔧 API 테스트 예시

### 1. 전체 플로우 테스트

```bash
# 1. 토큰 발급
TOKEN_RESPONSE=$(curl -s -X POST http://localhost:8080/api/queue/token \
  -H "Content-Type: application/json" \
  -d '{"userId": "user-123"}')

TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.data.token')
echo "발급받은 토큰: $TOKEN"

# 2. 대기열 상태 확인 (폴링)
curl -X GET http://localhost:8080/api/queue/status \
  -H "Authorization: Bearer $TOKEN"

# 3. 콘서트 날짜 조회 (토큰 불필요)
curl -X GET http://localhost:8080/api/concerts/available-dates

# 4. 좌석 조회 (토큰 불필요)
curl -X GET http://localhost:8080/api/concerts/1/seats

# 5. 잔액 조회 (토큰 불필요)
curl -X GET http://localhost:8080/api/users/user-123/balance

# 6. 잔액 충전 (토큰 필요, 대기열 통과 후)
curl -X POST http://localhost:8080/api/users/user-123/balance \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100000}'

# 7. 좌석 예약 (토큰 필요, 대기열 통과 후)
RESERVATION_RESPONSE=$(curl -s -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"concertId": 1, "seatNumber": 15, "userId": "user-123"}')

RESERVATION_ID=$(echo $RESERVATION_RESPONSE | jq -r '.data.reservationId')
echo "예약 ID: $RESERVATION_ID"

# 8. 예약 상태 조회
curl -X GET http://localhost:8080/api/reservations/$RESERVATION_ID \
  -H "Authorization: Bearer $TOKEN"

# 9. 결제 (토큰 필요)
PAYMENT_RESPONSE=$(curl -s -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"reservationId\": \"$RESERVATION_ID\", \"userId\": \"user-123\"}")

PAYMENT_ID=$(echo $PAYMENT_RESPONSE | jq -r '.data.paymentId')
echo "결제 ID: $PAYMENT_ID"

# 10. 결제 내역 조회 (토큰 불필요)
curl -X GET http://localhost:8080/api/payments/$PAYMENT_ID
```

### 2. 동시성 테스트

```bash
# 같은 좌석에 대한 동시 예약 테스트
# 터미널 1
curl -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer $TOKEN1" \
  -H "Content-Type: application/json" \
  -d '{"concertId": 1, "seatNumber": 15, "userId": "user-1"}' &

# 터미널 2 (동시 실행)
curl -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer $TOKEN2" \
  -H "Content-Type: application/json" \
  -d '{"concertId": 1, "seatNumber": 15, "userId": "user-2"}' &

# 결과: 하나는 성공, 하나는 409 Conflict
```

### 3. 에러 케이스 테스트

```bash
# 잘못된 데이터 타입
curl -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"concertId": "abc", "seatNumber": 15, "userId": "user-123"}'
# 응답: 400 Bad Request

# 존재하지 않는 콘서트
curl -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"concertId": 999, "seatNumber": 15, "userId": "user-123"}'
# 응답: 404 Not Found

# 토큰 없이 대기열 필요 API 호출
curl -X POST http://localhost:8080/api/reservations \
  -H "Content-Type: application/json" \
  -d '{"concertId": 1, "seatNumber": 15, "userId": "user-123"}'
# 응답: 401 Unauthorized

# 잔액 부족 시 결제
curl -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reservationId": "$RESERVATION_ID", "userId": "user-123"}'
# 응답: 400 Bad Request (INSUFFICIENT_BALANCE)
```

---

## 🔍 API 개발 가이드

### 1. 데이터 검증 체크리스트

#### Request 검증
- [ ] 필수 필드 존재 여부
- [ ] 데이터 타입 일치 (integer, string, UUID 형식)
- [ ] 범위 검증 (seatNumber: 1-50, amount: 10,000-1,000,000)
- [ ] 외래키 존재 여부 (concertId, userId 등)

#### 비즈니스 로직 검증
- [ ] 대기열 토큰 상태 (ACTIVE만 허용)
- [ ] 좌석 상태 (AVAILABLE만 예약 가능)
- [ ] 잔액 충분 여부
- [ ] 예약 만료 시간 확인

### 2. 트랜잭션 경계

#### 좌석 예약 트랜잭션
```sql
BEGIN;
-- 1. 좌석 상태 확인 및 락
SELECT * FROM seats WHERE seat_id = ? FOR UPDATE;
-- 2. 좌석 상태 업데이트
UPDATE seats SET status = 'TEMPORARILY_ASSIGNED', assigned_user_id = ?, assigned_until = ? WHERE seat_id = ?;
-- 3. 예약 레코드 생성
INSERT INTO reservations (...) VALUES (...);
COMMIT;
```

#### 결제 트랜잭션
```sql
BEGIN;
-- 1. 예약 상태 확인
SELECT * FROM reservations WHERE reservation_id = ? FOR UPDATE;
-- 2. 잔액 확인 및 차감
UPDATE users SET balance = balance - ? WHERE user_id = ? AND balance >= ?;
-- 3. 결제 레코드 생성
INSERT INTO payments (...) VALUES (...);
-- 4. 좌석 확정 처리
UPDATE seats SET status = 'RESERVED', reserved_at = NOW() WHERE seat_id = ?;
-- 5. 예약 확정 처리
UPDATE reservations SET status = 'CONFIRMED', confirmed_at = NOW() WHERE reservation_id = ?;
-- 6. 잔액 거래 내역 생성
INSERT INTO balance_transactions (...) VALUES (...);
COMMIT;
```

### 3. 캐시 전략

#### Redis 캐싱 대상
- **콘서트 목록**: TTL 10분 (자주 변경되지 않음)
- **사용자 잔액**: TTL 1분 (결제 시 실시간 반영 필요)
- **좌석 현황**: 캐싱 안함 (실시간 정확성 중요)

#### 캐시 무효화
```
잔액 충전/결제 → 사용자 잔액 캐시 삭제
콘서트 정보 변경 → 콘서트 목록 캐시 삭제
```

### 4. 에러 처리 패턴

#### Controller Layer
```java
@PostMapping("/reservations")
public ResponseEntity<?> createReservation(@RequestBody ReservationRequest request) {
    try {
        // 비즈니스 로직 실행
        ReservationResponse response = reservationService.createReservation(request);
        return ResponseEntity.status(201).body(ApiResponse.success(response));
    } catch (SeatNotAvailableException e) {
        return ResponseEntity.status(409).body(ApiResponse.error("SEAT_NOT_AVAILABLE", e.getMessage()));
    } catch (InsufficientBalanceException e) {
        return ResponseEntity.status(400).body(ApiResponse.error("INSUFFICIENT_BALANCE", e.getMessage()));
    }
}
```

#### Global Exception Handler
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(400).body(
            ApiResponse.error("INVALID_DATA_TYPE", "잘못된 데이터 타입입니다.")
        );
    }
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(
            ApiResponse.error("DATA_INTEGRITY_VIOLATION", "데이터 무결성 위반입니다.")
        );
    }
}
```

---

## 📚 관련 문서

- [요구사항 명세서](./requirements.md)
- [데이터베이스 ERD](./erd.md)
- [시퀀스 다이어그램](./sequence-diagrams.md)
- [시스템 아키텍처](./architecture.md)