package kr.hhplus.be.server.balance.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "balance_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BalanceTransaction {

    @Id
    @Column(name = "transaction_id", length = 36)
    private String transactionId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount; // Long → BigDecimal로 변경

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter; // Long → BigDecimal로 변경

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum TransactionType {
        CHARGE, PAYMENT, REFUND
    }

    /**
     * 잔액 거래 내역을 생성
     * 거래 ID는 자동으로 UUID로 생성되며, 생성 시간은 현재 시간으로 설정
     *
     * @param userId 사용자 ID (필수, 50자 이하)
     * @param transactionType 거래 유형 (필수)
     * @param amount 거래 금액 (필수, 0보다 큰 값)
     * @param balanceAfter 거래 후 잔액 (필수, 0 이상)
     * @param description 거래 설명 (선택, 500자 이하)
     * @throws IllegalArgumentException 입력 데이터가 유효하지 않은 경우
     */
    public BalanceTransaction(String userId, TransactionType transactionType, BigDecimal amount,
                              BigDecimal balanceAfter, String description) {
        validateTransactionData(userId, transactionType, amount, balanceAfter, description);

        this.transactionId = UUID.randomUUID().toString();
        this.userId = userId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 잔액 충전 거래를 생성
     *
     * @param userId 사용자 ID
     * @param amount 충전 금액
     * @param balanceAfter 충전 후 잔액
     * @return 충전 거래 객체
     */
    public static BalanceTransaction charge(String userId, BigDecimal amount, BigDecimal balanceAfter) {
        return new BalanceTransaction(userId, TransactionType.CHARGE, amount, balanceAfter, "잔액 충전");
    }

    /**
     * 좌석 예약 결제 거래를 생성
     *
     * @param userId 사용자 ID
     * @param amount 결제 금액
     * @param balanceAfter 결제 후 잔액
     * @param reservationId 예약 ID
     * @return 결제 거래 객체
     */
    public static BalanceTransaction payment(String userId, BigDecimal amount, BigDecimal balanceAfter, String reservationId) {
        String description = "좌석 예약 결제 - 예약ID: " + reservationId;
        return new BalanceTransaction(userId, TransactionType.PAYMENT, amount, balanceAfter, description);
    }

    /**
     * 환불 거래를 생성
     *
     * @param userId 사용자 ID
     * @param amount 환불 금액
     * @param balanceAfter 환불 후 잔액
     * @param reason 환불 사유
     * @return 환불 거래 객체
     */
    public static BalanceTransaction refund(String userId, BigDecimal amount, BigDecimal balanceAfter, String reason) {
        String description = "환불 - " + reason;
        return new BalanceTransaction(userId, TransactionType.REFUND, amount, balanceAfter, description);
    }

    /**
     * 상세 설명이 포함된 잔액 충전 거래를 생성
     *
     * @param userId 사용자 ID
     * @param amount 충전 금액
     * @param balanceAfter 충전 후 잔액
     * @param detail 충전 상세 설명
     * @return 충전 거래 객체
     */
    public static BalanceTransaction chargeWithDetail(String userId, BigDecimal amount, BigDecimal balanceAfter, String detail) {
        String description = "잔액 충전 - " + detail;
        return new BalanceTransaction(userId, TransactionType.CHARGE, amount, balanceAfter, description);
    }

    /**
     * 콘서트 정보가 포함된 좌석 예약 결제 거래를 생성
     *
     * @param userId 사용자 ID
     * @param amount 결제 금액
     * @param balanceAfter 결제 후 잔액
     * @param reservationId 예약 ID
     * @param concertTitle 콘서트 제목
     * @return 결제 거래 객체
     */
    public static BalanceTransaction paymentWithDetail(String userId, BigDecimal amount, BigDecimal balanceAfter,
                                                       String reservationId, String concertTitle) {
        String description = String.format("좌석 예약 결제 - 예약ID: %s, 콘서트: %s", reservationId, concertTitle);
        return new BalanceTransaction(userId, TransactionType.PAYMENT, amount, balanceAfter, description);
    }

    /**
     * 충전 거래인지 확인
     *
     * @return 충전 거래이면 true, 아니면 false
     */
    public boolean isChargeTransaction() {
        return transactionType == TransactionType.CHARGE;
    }

    /**
     * 결제 거래인지 확인
     *
     * @return 결제 거래이면 true, 아니면 false
     */
    public boolean isPaymentTransaction() {
        return transactionType == TransactionType.PAYMENT;
    }

    /**
     * 환불 거래인지 확인
     *
     * @return 환불 거래이면 true, 아니면 false
     */
    public boolean isRefundTransaction() {
        return transactionType == TransactionType.REFUND;
    }

    /**
     * 출금(차감) 거래인지 확인
     * 현재는 결제 거래만 출금으로 처리됩니다.
     *
     * @return 출금 거래이면 true, 아니면 false
     */
    public boolean isDebitTransaction() {
        return transactionType == TransactionType.PAYMENT;
    }

    /**
     * 입금(증가) 거래인지 확인
     * 충전과 환불 거래가 입금으로 처리됩니다.
     *
     * @return 입금 거래이면 true, 아니면 false
     */
    public boolean isCreditTransaction() {
        return transactionType == TransactionType.CHARGE || transactionType == TransactionType.REFUND;
    }

    /**
     * 거래 금액이 유효한지 확인
     *
     * @return 금액이 null이 아니고 0보다 크면 true, 아니면 false
     */
    public boolean isAmountValid() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 거래 후 잔액이 유효한지 확인
     *
     * @return 잔액이 null이 아니고 0 이상이면 true, 아니면 false
     */
    public boolean isBalanceAfterValid() {
        return balanceAfter != null && balanceAfter.compareTo(BigDecimal.ZERO) >= 0;
    }

    /**
     * 거래 전 잔액을 계산
     * 출금 거래의 경우: 거래 후 잔액 + 거래 금액
     * 입금 거래의 경우: 거래 후 잔액 - 거래 금액
     *
     * @return 거래 전 잔액
     */
    public BigDecimal getBalanceBeforeTransaction() {
        if (isDebitTransaction()) {
            return balanceAfter.add(amount);
        } else {
            return balanceAfter.subtract(amount);
        }
    }

    /**
     * 거래 설명이 존재하는지 확인
     *
     * @return 설명이 null이 아니고 공백이 아니면 true, 아니면 false
     */
    public boolean hasDescription() {
        return description != null && !description.trim().isEmpty();
    }

    /**
     * 거래 설명을 50자 이내로 축약하여 반환
     * 50자를 초과하는 경우 47자까지만 표시하고 "..."을 추가합니다.
     *
     * @return 축약된 설명 문자열 (최대 50자)
     */
    public String getShortDescription() {
        if (description == null) return "";
        return description.length() > 50 ? description.substring(0, 47) + "..." : description;
    }

    /**
     * 거래 데이터의 유효성을 검증합니다.
     * 검증 조건:
     * - 사용자 ID: 필수, 50자 이하
     * - 거래 유형: 필수
     * - 거래 금액: 필수, 0보다 큰 값
     * - 거래 후 잔액: 필수, 0 이상
     * - 설명: 선택, 500자 이하
     *
     * @param userId 사용자 ID
     * @param transactionType 거래 유형
     * @param amount 거래 금액
     * @param balanceAfter 거래 후 잔액
     * @param description 거래 설명
     * @throws IllegalArgumentException 유효하지 않은 데이터가 전달된 경우
     */
    private void validateTransactionData(String userId, TransactionType transactionType,
                                         BigDecimal amount, BigDecimal balanceAfter, String description) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (userId.length() > 50) {
            throw new IllegalArgumentException("사용자 ID는 50자를 초과할 수 없습니다.");
        }

        if (transactionType == null) {
            throw new IllegalArgumentException("거래 유형은 필수입니다.");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("거래 금액은 0보다 커야 합니다.");
        }

        if (balanceAfter == null || balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("거래 후 잔액은 0 이상이어야 합니다.");
        }

        if (description != null && description.length() > 500) {
            throw new IllegalArgumentException("설명은 500자를 초과할 수 없습니다.");
        }
    }
}