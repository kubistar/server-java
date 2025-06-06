package kr.hhplus.be.server;

import kr.hhplus.be.server.command.BalanceChargeCommand;
import kr.hhplus.be.server.domain.BalanceTransaction;
import kr.hhplus.be.server.domain.User;
import kr.hhplus.be.server.dto.BalanceResult;
import kr.hhplus.be.server.repository.BalanceTransactionRepository;
import kr.hhplus.be.server.repository.UserRepository;
import kr.hhplus.be.server.service.BalanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BalanceTransactionRepository balanceTransactionRepository;

    @InjectMocks
    private BalanceService balanceService;

    private BalanceChargeCommand command;
    private User existingUser;

    @BeforeEach
    void setUp() {
        command = new BalanceChargeCommand("user-123", 100000L);
        existingUser = new User("user-123", 50000L);
    }

    @Test
    @DisplayName("기존 사용자의 잔액 충전이 성공한다")
    void whenChargeBalanceForExistingUser_ThenShouldSucceed() {
        // given
        given(userRepository.findByIdForUpdate("user-123")).willReturn(Optional.of(existingUser));
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(balanceTransactionRepository.save(any(BalanceTransaction.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        BalanceResult result = balanceService.chargeBalance(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getCurrentBalance()).isEqualTo(150000L);

        // 검증: 사용자 저장됨
        verify(userRepository).save(argThat(user ->
                user.getBalance().equals(150000L)
        ));

        // 검증: 거래 내역 저장됨
        verify(balanceTransactionRepository).save(argThat(transaction ->
                transaction.getTransactionType() == BalanceTransaction.TransactionType.CHARGE &&
                        transaction.getAmount().equals(100000L) &&
                        transaction.getBalanceAfter().equals(150000L)
        ));
    }

    @Test
    @DisplayName("신규 사용자의 잔액 충전이 성공한다")
    void whenChargeBalanceForNewUser_ThenShouldSucceed() {
        // given
        given(userRepository.findByIdForUpdate("user-123")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(balanceTransactionRepository.save(any(BalanceTransaction.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        BalanceResult result = balanceService.chargeBalance(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getCurrentBalance()).isEqualTo(100000L); // 0 + 100000

        // 검증: 새 사용자가 생성되고 저장됨
        verify(userRepository).save(argThat(user ->
                user.getUserId().equals("user-123") &&
                        user.getBalance().equals(100000L)
        ));
    }

    @Test
    @DisplayName("사용자 잔액 조회가 성공한다")
    void whenGetBalance_ThenShouldReturnCorrectBalance() {
        // given
        given(userRepository.findById("user-123")).willReturn(Optional.of(existingUser));

        // when
        BalanceResult result = balanceService.getBalance("user-123");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getCurrentBalance()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 조회 시 잔액 0으로 반환한다")
    void whenGetBalanceForNonExistentUser_ThenShouldReturnZeroBalance() {
        // given
        given(userRepository.findById("user-123")).willReturn(Optional.empty());

        // when
        BalanceResult result = balanceService.getBalance("user-123");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getCurrentBalance()).isEqualTo(0L);
    }
}
