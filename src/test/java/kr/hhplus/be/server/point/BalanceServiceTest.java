package kr.hhplus.be.server.point;

import kr.hhplus.be.server.balance.command.BalanceChargeCommand;
import kr.hhplus.be.server.balance.domain.Balance;
import kr.hhplus.be.server.balance.domain.BalanceTransaction;
import kr.hhplus.be.server.balance.dto.BalanceResult;
import kr.hhplus.be.server.balance.repository.BalanceRepository;
import kr.hhplus.be.server.balance.repository.BalanceTransactionRepository;
import kr.hhplus.be.server.balance.service.BalanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private BalanceRepository balanceRepository; // UserRepository → BalanceRepository

    @Mock
    private BalanceTransactionRepository balanceTransactionRepository;

    @InjectMocks
    private BalanceService balanceService;

    private BalanceChargeCommand command;
    private Balance existingBalance;

    @BeforeEach
    void setUp() {
        command = new BalanceChargeCommand("user-123", 100000L);
        existingBalance = new Balance("user-123", BigDecimal.valueOf(50000)); // User → Balance
    }

    @Test
    @DisplayName("기존 사용자의 잔액 충전이 성공한다")
    void whenChargeBalanceForExistingUser_ThenShouldSucceed() {
        // given
        given(balanceRepository.findByUserIdWithLock("user-123")).willReturn(Optional.of(existingBalance));
        given(balanceRepository.save(any(Balance.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(balanceTransactionRepository.save(any(BalanceTransaction.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        BalanceResult result = balanceService.chargeBalance(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getCurrentBalance()).isEqualTo(BigDecimal.valueOf(150000)); // Long → BigDecimal

        // 검증: 잔액 저장됨
        verify(balanceRepository).save(argThat(balance ->
                balance.getAmount().equals(BigDecimal.valueOf(150000))
        ));

        // 검증: 거래 내역 저장됨
        verify(balanceTransactionRepository).save(argThat(transaction ->
                transaction.getTransactionType() == BalanceTransaction.TransactionType.CHARGE &&
                        transaction.getAmount().equals(BigDecimal.valueOf(100000)) &&
                        transaction.getBalanceAfter().equals(BigDecimal.valueOf(150000))
        ));
    }

    @Test
    @DisplayName("신규 사용자의 잔액 충전이 성공한다")
    void whenChargeBalanceForNewUser_ThenShouldSucceed() {
        // given
        given(balanceRepository.findByUserIdWithLock("user-123")).willReturn(Optional.empty());
        given(balanceRepository.save(any(Balance.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(balanceTransactionRepository.save(any(BalanceTransaction.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        BalanceResult result = balanceService.chargeBalance(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getCurrentBalance()).isEqualTo(BigDecimal.valueOf(100000)); // 0 + 100000

        // 검증: 새 잔액이 생성되고 저장됨
        verify(balanceRepository).save(argThat(balance ->
                balance.getUserId().equals("user-123") &&
                        balance.getAmount().equals(BigDecimal.valueOf(100000))
        ));
    }

    @Test
    @DisplayName("사용자 잔액 조회가 성공한다")
    void whenGetBalance_ThenShouldReturnCorrectBalance() {
        // given
        given(balanceRepository.findByUserId("user-123")).willReturn(Optional.of(existingBalance));

        // when
        BalanceResult result = balanceService.getBalance("user-123");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getCurrentBalance()).isEqualTo(BigDecimal.valueOf(50000));
    }

    @Test
    @DisplayName("존재하지 않는 사용자 조회 시 잔액 0으로 반환한다")
    void whenGetBalanceForNonExistentUser_ThenShouldReturnZeroBalance() {
        // given
        given(balanceRepository.findByUserId("user-123")).willReturn(Optional.empty());

        // when
        BalanceResult result = balanceService.getBalance("user-123");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getCurrentBalance()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("잔액 차감이 성공한다")
    void whenDeductBalance_ThenShouldSucceed() {
        // given
        BigDecimal deductAmount = BigDecimal.valueOf(30000);
        String reservationId = "res-123";

        given(balanceRepository.findByUserIdWithLock("user-123")).willReturn(Optional.of(existingBalance));
        given(balanceRepository.save(any(Balance.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(balanceTransactionRepository.save(any(BalanceTransaction.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Balance result = balanceService.deductBalance("user-123", deductAmount, reservationId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualTo(BigDecimal.valueOf(20000)); // 50000 - 30000

        // 검증: 잔액 저장됨
        verify(balanceRepository).save(argThat(balance ->
                balance.getAmount().equals(BigDecimal.valueOf(20000))
        ));

        // 검증: 거래 내역 저장됨
        verify(balanceTransactionRepository).save(argThat(transaction ->
                transaction.getTransactionType() == BalanceTransaction.TransactionType.PAYMENT &&
                        transaction.getAmount().equals(deductAmount) &&
                        transaction.getBalanceAfter().equals(BigDecimal.valueOf(20000))
        ));
    }

    @Test
    @DisplayName("잔액 부족 시 차감이 실패한다")
    void whenDeductBalanceWithInsufficientAmount_ThenShouldThrowException() {
        // given
        BigDecimal deductAmount = BigDecimal.valueOf(60000); // 현재 잔액(50000)보다 큰 금액
        String reservationId = "res-123";

        given(balanceRepository.findByUserIdWithLock("user-123")).willReturn(Optional.of(existingBalance));

        // when & then
        assertThatThrownBy(() -> balanceService.deductBalance("user-123", deductAmount, reservationId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("잔액이 부족합니다");

        // 검증: 잔액이 저장되지 않음
        verify(balanceRepository, never()).save(any(Balance.class));
        verify(balanceTransactionRepository, never()).save(any(BalanceTransaction.class));
    }

    @Test
    @DisplayName("환불 처리가 성공한다")
    void whenRefundBalance_ThenShouldSucceed() {
        // given
        BigDecimal refundAmount = BigDecimal.valueOf(25000);
        String reason = "예약 취소";

        given(balanceRepository.findByUserIdWithLock("user-123")).willReturn(Optional.of(existingBalance));
        given(balanceRepository.save(any(Balance.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(balanceTransactionRepository.save(any(BalanceTransaction.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Balance result = balanceService.refundBalance("user-123", refundAmount, reason);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualTo(BigDecimal.valueOf(75000)); // 50000 + 25000

        // 검증: 잔액 저장됨
        verify(balanceRepository).save(argThat(balance ->
                balance.getAmount().equals(BigDecimal.valueOf(75000))
        ));

        // 검증: 거래 내역 저장됨
        verify(balanceTransactionRepository).save(argThat(transaction ->
                transaction.getTransactionType() == BalanceTransaction.TransactionType.REFUND &&
                        transaction.getAmount().equals(refundAmount) &&
                        transaction.getBalanceAfter().equals(BigDecimal.valueOf(75000))
        ));
    }

    @Test
    @DisplayName("잔액 충분성 확인이 정확히 동작한다")
    void whenHasEnoughBalance_ThenShouldReturnCorrectResult() {
        // given
        BigDecimal requiredAmount1 = BigDecimal.valueOf(30000); // 충분함
        BigDecimal requiredAmount2 = BigDecimal.valueOf(60000); // 부족함

        given(balanceRepository.hasEnoughAmount("user-123", requiredAmount1)).willReturn(Optional.of(true));
        given(balanceRepository.hasEnoughAmount("user-123", requiredAmount2)).willReturn(Optional.of(false));

        // when & then
        assertThat(balanceService.hasEnoughBalance("user-123", requiredAmount1)).isTrue();
        assertThat(balanceService.hasEnoughBalance("user-123", requiredAmount2)).isFalse();
    }
}