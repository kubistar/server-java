# API 명세서 (API Specification)

## 📋 개요

콘서트 예약 서비스의 REST API 명세서입니다.

### 기본 정보
- **Base URL**: `http://localhost:8080`
- **Content-Type**: `application/json`
- **Database**: MySQL + Redis

### 응답 형식 (Response Format)

#### 성공 응답
```json
{
  "code": 200,
  "data": { ... },
  "message": "성공 메시지"
}
```

#### 에러 응답
```json
{
  "code": 400,
  "error": {
    "type": "INVALID_DATA_TYPE",
    "message": "상세 에러 메시지",
    "details": { ... }
  },
  "timestamp": "2025-05-29T15:35:00Z"
}
```

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
  "code": 201,
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
  "code": 400,
  "error": {
    "type": "INVALID_USER_ID",
    "message": "유효하지 않은 사용자 ID입니다.",
    "details": {
      "userId": "user-123",
      "reason": "User ID length must be between 3 and 50 characters"
    }
  },
  "timestamp": "2025-05-29T15:20:00Z"
}

// 409 Conflict
{
  "code": 409,
  "error": {
    "type": "TOKEN_ALREADY_EXISTS",
    "message": "이미 발급된 토큰이 존재합니다.",
    "details": {
      "existingToken": "550e8400-e29b-41d4-a716-446655440000",
      "expiresAt": "2025-05-29T16:20:00Z"
    }
  },
  "timestamp": "2025-05-29T15:20:00Z"
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
  "code": 200,
  "data": {
    "token": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "user-123",
    "status": "WAITING",
    "queuePosition": 120,
    "estimatedWaitTimeMinutes": 12,
    "totalInQueue": 1500,
    "activeUsers": 100,
    "maxActiveUsers": 200
  },
  "message": "대기열 상태 조회 성공"
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
  "code": 200,
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
  },
  "message": "콘서트 목록 조회 성공"
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
  "code": 200,
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
  },
  "message": "좌석 정보 조회 성공"
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
  "code": 201,
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
  "code": 409,
  "error": {
    "type": "SEAT_NOT_AVAILABLE",
    "message": "선택한 좌석은 이미 다른 사용자가 예약했습니다.",
    "details": {
      "seatNumber": 15,
      "currentStatus": "RESERVED",
      "reservedAt": "2025-05-29T14:30:00Z"
    }
  },
  "timestamp": "2025-05-29T15:30:00Z"
}

// 409 Conflict - 동시 예약 시도
{
  "code": 409,
  "error": {
    "type": "CONCURRENT_RESERVATION_CONFLICT",
    "message": "다른 사용자가 같은 좌석을 처리 중입니다. 잠시 후 재시도해주세요.",
    "details": {
      "seatNumber": 15,
      "retryAfterSeconds": 3
    }
  },
  "timestamp": "2025-05-29T15:30:00Z"
}

// 403 Forbidden - 대기열 토큰 비활성
{
  "code": 403,
  "error": {
    "type": "QUEUE_TOKEN_NOT_ACTIVE",
    "message": "대기열에서 순서를 기다려주세요.",
    "details": {
      "queuePosition": 50,
      "estimatedWaitTimeMinutes": 5,
      "currentStatus": "WAITING"
    }
  },
  "timestamp": "2025-05-29T15:30:00Z"
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
  "code": 200,
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
  },
  "message": "예약 상태 조회 성공"
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
  "code": 200,
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

#### Error Responses
```json
// 400 Bad Request - 잘못된 충전 금액
{
  "code": 400,
  "error": {
    "type": "INVALID_CHARGE_AMOUNT",
    "message": "충전 금액은 10,000원 이상 1,000,000원 이하여야 합니다.",
    "details": {
      "requestedAmount": 5000,
      "minAmount": 10000,
      "maxAmount": 1000000,
      "requiredUnit": 1000
    }
  },
  "timestamp": "2025-05-29T15:25:00Z"
}
```

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
  "code": 200,
  "data": {
    "userId": "user-123",
    "currentBalance": 150000,
    "lastTransactionAt": "2025-05-29T15:25:00Z"
  },
  "message": "잔액 조회 성공"
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
  "code": 200,
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
  "code": 400,
  "error": {
    "type": "INSUFFICIENT_BALANCE",
    "message": "잔액이 부족합니다.",
    "details": {
      "currentBalance": 30000,
      "requiredAmount": 50000,
      "shortfallAmount": 20000
    }
  },
  "timestamp": "2025-05-29T15:32:00Z"
}

// 400 Bad Request - 만료된 임시 배정
{
  "code": 400,
  "error": {
    "type": "RESERVATION_EXPIRED",
    "message": "예약 시간이 만료되었습니다. 다시 예약해주세요.",
    "details": {
      "reservationId": "550e8400-e29b-41d4-a716-446655440001",
      "expiredAt": "2025-05-29T15:35:00Z",
      "currentTime": "2025-05-29T15:36:00Z"
    }
  },
  "timestamp": "2025-05-29T15:36:00Z"
}

// 409 Conflict - 이미 결제된 예약
{
  "code": 409,
  "error": {
    "type": "ALREADY_PAID",
    "message": "이미 결제가 완료된 예약입니다.",
    "details": {
      "paymentId": "550e8400-e29b-41d4-a716-446655440003",
      "paidAt": "2025-05-29T15:32:00Z",
      "amount": 50000
    }
  },
  "timestamp": "2025-05-29T15:32:05Z"
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
  "code": 200,
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
  },
  "message": "결제 내역 조회 성공"
}
```

---

## 🚨 공통 에러 응답

### 인증 관련 에러

```json
// 401 Unauthorized - 토큰 없음
{
  "code": 401,
  "error": {
    "type": "MISSING_TOKEN",
    "message": "대기열 토큰이 필요합니다.",
    "details": {
      "requiredHeader": "Authorization: Bearer {token}"
    }
  },
  "timestamp": "2025-05-29T15:35:00Z"
}

// 401 Unauthorized - 유효하지 않은 토큰
{
  "code": 401,
  "error": {
    "type": "INVALID_TOKEN",
    "message": "유효하지 않은 토큰입니다.",
    "details": {
      "tokenFormat": "UUID",
      "providedToken": "invalid-token-format"
    }
  },
  "timestamp": "2025