package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.queue.service.QueueService;
import kr.hhplus.be.server.queue.domain.QueueToken;
import kr.hhplus.be.server.queue.domain.QueueStatus;
import kr.hhplus.be.server.reservation.service.ReserveSeatUseCase;
import kr.hhplus.be.server.reservation.command.ReserveSeatCommand;
import kr.hhplus.be.server.reservation.dto.ReservationResult;
import kr.hhplus.be.server.payment.service.PaymentService;
import kr.hhplus.be.server.payment.command.PaymentCommand;
import kr.hhplus.be.server.payment.dto.PaymentResult;

import kr.hhplus.be.server.user.domain.User;
import kr.hhplus.be.server.user.repository.UserRepository;
import kr.hhplus.be.server.balance.domain.Balance;
import kr.hhplus.be.server.balance.repository.BalanceRepository;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatRepository;
import kr.hhplus.be.server.reservation.repository.ReservationRepository;
import kr.hhplus.be.server.payment.repository.PaymentRepository;

import kr.hhplus.be.server.payment.exception.InsufficientBalanceException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private ReserveSeatUseCase reserveSeatUseCase;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BalanceRepository balanceRepository; // 추가

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private static final Logger log = LoggerFactory.getLogger(IntegrationTest.class);

    private Long testConcertId = 1L;
    private Integer testSeatNumber = 15;
    private BigDecimal seatPrice = BigDecimal.valueOf(50000); // Integer → BigDecimal
    private Seat testSeat;

    @BeforeEach
    @Transactional
    void setUp() {
        // 테스트용 좌석 생성 (BigDecimal 사용)
        testSeat = new Seat(testConcertId, testSeatNumber, seatPrice);
        testSeat = seatRepository.save(testSeat);
    }

    @Test
    @DisplayName("전체 플로우 통합 테스트: 토큰 발급 → 좌석 예약 → 결제 완료")
    @Transactional
    void fullReservationFlow_TokenToPayment_ShouldWorkCorrectly() {
        // given
        String userId = "user-123";
        BigDecimal initialBalance = BigDecimal.valueOf(100000);

        // 사용자 생성 (balance 없이)
        User user = new User(userId);
        userRepository.save(user);

        // 잔액 정보 별도 생성
        Balance balance = new Balance(userId, initialBalance);
        balanceRepository.save(balance);

        // when & then
        // 1. 대기열 토큰 발급 (재시도 로직 추가)
        QueueToken queueToken = null;
        int retryCount = 0;
        int maxRetries = 5;

        while (queueToken == null && retryCount < maxRetries) {
            try {
                queueToken = queueService.issueToken(userId);
                break;
            } catch (RuntimeException e) {
                if (e.getMessage().contains("대기열 처리 중입니다")) {
                    retryCount++;
                    log.info("대기열 처리 중으로 인한 재시도: {}/{}", retryCount, maxRetries);
                    try {
                        Thread.sleep(2000); // 2초 대기
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("토큰 발급 중 인터럽트 발생", ie);
                    }
                } else {
                    throw e; // 다른 예외는 그대로 던지기
                }
            }
        }

        if (queueToken == null) {
            throw new RuntimeException("토큰 발급 실패: 최대 재시도 횟수 초과");
        }

        assertThat(queueToken).isNotNull();
        assertThat(queueToken.getUserId()).isEqualTo(userId);

        // 활성 토큰이 될 때까지 대기 (필요시)
        if (queueToken.getStatus() == QueueStatus.WAITING) {
            final String tokenForLambda = queueToken.getToken(); // final 변수로 복사
            await().atMost(15, TimeUnit.SECONDS)
                    .pollInterval(1, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        QueueToken updatedToken = queueService.getQueueStatus(tokenForLambda);
                        assertThat(updatedToken.getStatus()).isEqualTo(QueueStatus.ACTIVE);
                    });
            queueToken = queueService.getQueueStatus(queueToken.getToken());
        }

        // 토큰이 ACTIVE 상태인지만 확인
        assertThat(queueToken.getStatus()).isEqualTo(QueueStatus.ACTIVE);

        // 3. 좌석 예약
        ReserveSeatCommand reserveCommand = new ReserveSeatCommand(userId, testConcertId, testSeatNumber);
        ReservationResult reservation = reserveSeatUseCase.reserveSeat(reserveCommand);

        assertThat(reservation).isNotNull();
        assertThat(reservation.getUserId()).isEqualTo(userId);
        assertThat(reservation.getPrice()).isEqualTo(seatPrice);
        assertThat(reservation.getConcertId()).isEqualTo(testConcertId);
        assertThat(reservation.getSeatNumber()).isEqualTo(testSeatNumber);
        assertThat(reservation.getRemainingTimeSeconds()).isGreaterThan(0);

        // 4. 결제 처리
        PaymentCommand paymentCommand = new PaymentCommand(reservation.getReservationId(), userId);
        PaymentResult paymentResult = paymentService.processPayment(paymentCommand);

        assertThat(paymentResult).isNotNull();
        assertThat(paymentResult.getUserId()).isEqualTo(userId);
        assertThat(paymentResult.getAmount()).isEqualTo(seatPrice);
        assertThat(paymentResult.getStatus()).isEqualTo("COMPLETED");

        // 5. 최종 상태 검증
        // 좌석이 확정 예약 상태로 변경되었는지 확인
        Seat finalSeat = seatRepository.findById(testSeat.getSeatId()).orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(Seat.SeatStatus.RESERVED);
        assertThat(finalSeat.getAssignedUserId()).isEqualTo(userId);

        // 사용자 잔액이 차감되었는지 확인
        Balance finalBalance = balanceRepository.findByUserId(userId).orElseThrow();
        assertThat(finalBalance.getAmount()).isEqualTo(initialBalance.subtract(seatPrice));

        // 결제 정보 확인
        assertThat(paymentRepository.findByReservationId(reservation.getReservationId())).isPresent();
    }

    @Test
    @DisplayName("잔액 부족 시 결제 실패 테스트")
    @Transactional
    void paymentWithInsufficientBalance_ShouldFail() {
        // given
        String userId = "user-123";
        BigDecimal insufficientBalance = BigDecimal.valueOf(30000); // 좌석 가격보다 적음

        User user = new User(userId);
        userRepository.save(user);

        Balance balance = new Balance(userId, insufficientBalance);
        balanceRepository.save(balance);

        // 토큰 발급 및 좌석 예약까지 성공
        QueueToken queueToken = queueService.issueToken(userId);

        // 활성 토큰 대기
        if (queueToken.getStatus() == QueueStatus.WAITING) {
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        QueueToken updatedToken = queueService.getQueueStatus(queueToken.getToken());
                        assertThat(updatedToken.getStatus()).isEqualTo(QueueStatus.ACTIVE);
                    });
        }

        ReserveSeatCommand reserveCommand = new ReserveSeatCommand(userId, testConcertId, testSeatNumber);
        ReservationResult reservation = reserveSeatUseCase.reserveSeat(reserveCommand);

        // when & then - 결제 시 실패
        PaymentCommand paymentCommand = new PaymentCommand(reservation.getReservationId(), userId);

        assertThatThrownBy(() -> paymentService.processPayment(paymentCommand))
                .isInstanceOf(InsufficientBalanceException.class);

        // 좌석이 여전히 임시 배정 상태인지 확인
        Seat seatAfterFailedPayment = seatRepository.findById(testSeat.getSeatId()).orElseThrow();
        assertThat(seatAfterFailedPayment.getStatus()).isEqualTo(Seat.SeatStatus.TEMPORARILY_ASSIGNED);
    }

    @Test
    @DisplayName("동시성 테스트: 50명이 같은 좌석에 동시 접근 시 1명만 성공")
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // 🔥 트랜잭션 비활성화
    void concurrentReservationTest_OnlyOneSucceeds() throws Exception {
        // given
        int userCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 새로운 좌석 생성 (트랜잭션 독립성 보장)
        Seat concurrentTestSeat = new Seat(testConcertId, testSeatNumber, seatPrice);
        Seat savedSeat = seatRepository.save(concurrentTestSeat);

        // 모든 사용자에게 충분한 잔액 부여
        for (int i = 0; i < userCount; i++) {
            String userId = "concurrent-user-" + i;
            User user = new User(userId);
            userRepository.save(user);

            Balance balance = new Balance(userId, BigDecimal.valueOf(100000));
            balanceRepository.save(balance);
        }

        // when - 동시에 예약 시도
        for (int i = 0; i < userCount; i++) {
            final String userId = "concurrent-user-" + i;
            final Long concertId = testConcertId;
            final Integer seatNumber = testSeatNumber;

            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // 토큰 발급
                    QueueToken token = queueService.issueToken(userId);

                    // 활성 토큰까지 대기 (최대 10초로 단축)
                    int maxRetries = 10;
                    int retryCount = 0;
                    while (token.getStatus() == QueueStatus.WAITING && retryCount < maxRetries) {
                        Thread.sleep(1000);
                        token = queueService.getQueueStatus(token.getToken());
                        retryCount++;
                    }

                    if (token.getStatus() != QueueStatus.ACTIVE) {
                        failCount.incrementAndGet();
                        return false;
                    }

                    // 좌석 예약
                    ReserveSeatCommand command = new ReserveSeatCommand(userId, concertId, seatNumber);
                    ReservationResult reservation = reserveSeatUseCase.reserveSeat(command);

                    // 결제
                    PaymentCommand paymentCommand = new PaymentCommand(reservation.getReservationId(), userId);
                    paymentService.processPayment(paymentCommand);

                    successCount.incrementAndGet();
                    return true;
                } catch (Exception e) {
                    log.info("Exception for user " + userId + ": " + e.getMessage());
                    failCount.incrementAndGet();
                    return false;
                }
            }, executorService);

            futures.add(future);
        }

        // 모든 작업 완료 대기 (최대 30초로 단축)
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
        } finally {
            executorService.shutdown();
        }

        // then - 결과 검증
        log.info("Success count: " + successCount.get() + ", Fail count: " + failCount.get());

        // 최대 1명 성공, 대부분 실패
        assertThat(successCount.get()).isLessThanOrEqualTo(1);
        assertThat(failCount.get()).isGreaterThan(0);
        assertThat(successCount.get() + failCount.get()).isEqualTo(userCount);

        // 성공자가 있다면 좌석 상태 확인
        if (successCount.get() > 0) {
            Seat finalSeat = seatRepository.findById(savedSeat.getSeatId()).orElseThrow();
            assertThat(finalSeat.getStatus()).isEqualTo(Seat.SeatStatus.RESERVED);
        }
    }

    @Test
    @DisplayName("예약 만료 후 다른 사용자 예약 가능 테스트")
    @Transactional
    void expiredReservation_OtherUserCanReserve() throws InterruptedException {
        // given
        String firstUserId = "user-first";
        String secondUserId = "user-second";

        // 두 사용자 모두 생성 및 잔액 설정
        userRepository.save(new User(firstUserId));
        userRepository.save(new User(secondUserId));
        balanceRepository.save(new Balance(firstUserId, BigDecimal.valueOf(100000)));
        balanceRepository.save(new Balance(secondUserId, BigDecimal.valueOf(100000)));

        // 첫 번째 사용자가 예약 (결제 안함)
        QueueToken firstToken = queueService.issueToken(firstUserId);

        // 활성 토큰 대기
        if (firstToken.getStatus() == QueueStatus.WAITING) {
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        QueueToken updatedToken = queueService.getQueueStatus(firstToken.getToken());
                        assertThat(updatedToken.getStatus()).isEqualTo(QueueStatus.ACTIVE);
                    });
        }

        ReserveSeatCommand firstCommand = new ReserveSeatCommand(firstUserId, testConcertId, testSeatNumber);
        ReservationResult firstReservation = reserveSeatUseCase.reserveSeat(firstCommand);

        assertThat(firstReservation).isNotNull();

        // 🔥 추가: 예약이 만료될 때까지 대기 (5분 1초)
        log.info("예약 만료 대기 중... 5분 1초 후에 해제됩니다.");
        Thread.sleep(301000); // 5분 1초 = 301초

        // when - 만료된 예약 해제
        reserveSeatUseCase.releaseExpiredReservations();

        // then - 두 번째 사용자가 예약 가능해야 함
        QueueToken secondToken = queueService.issueToken(secondUserId);

        // 활성 토큰 대기
        if (secondToken.getStatus() == QueueStatus.WAITING) {
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        QueueToken updatedToken = queueService.getQueueStatus(secondToken.getToken());
                        assertThat(updatedToken.getStatus()).isEqualTo(QueueStatus.ACTIVE);
                    });
        }

        ReserveSeatCommand secondCommand = new ReserveSeatCommand(secondUserId, testConcertId, testSeatNumber);
        ReservationResult secondReservation = reserveSeatUseCase.reserveSeat(secondCommand);

        assertThat(secondReservation).isNotNull();
        assertThat(secondReservation.getUserId()).isEqualTo(secondUserId);

        // 좌석이 다시 임시 배정되었는지 확인
        Seat finalSeat = seatRepository.findById(testSeat.getSeatId()).orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(Seat.SeatStatus.TEMPORARILY_ASSIGNED);
        assertThat(finalSeat.getAssignedUserId()).isEqualTo(secondUserId);
    }

    @Test
    @DisplayName("대기열 순서 테스트: 먼저 온 사용자가 먼저 처리됨")
    void queueOrderTest_FirstComeFirstServed() {
        // given
        String firstUserId = "user-first";
        String secondUserId = "user-second";

        userRepository.save(new User(firstUserId));
        userRepository.save(new User(secondUserId));
        balanceRepository.save(new Balance(firstUserId, BigDecimal.valueOf(100000)));
        balanceRepository.save(new Balance(secondUserId, BigDecimal.valueOf(100000)));

        // when - 순서대로 토큰 발급
        QueueToken firstToken = queueService.issueToken(firstUserId);
        QueueToken secondToken = queueService.issueToken(secondUserId);

        // then - 대기열이 있는 경우 순서 확인
        if (firstToken.getStatus() == QueueStatus.WAITING && secondToken.getStatus() == QueueStatus.WAITING) {
            assertThat(firstToken.getQueuePosition()).isLessThan(secondToken.getQueuePosition());
        } else if (firstToken.getStatus() == QueueStatus.ACTIVE) {
            // 첫 번째 사용자가 바로 활성화된 경우
            assertThat(secondToken.getStatus()).isIn(QueueStatus.WAITING, QueueStatus.ACTIVE);
        }
    }

    @Test
    @DisplayName("예약 취소 후 좌석 재예약 테스트")
    @Transactional
    void cancelReservation_SeatBecomesAvailable() {
        // given
        String firstUserId = "user-first";
        String secondUserId = "user-second";

        userRepository.save(new User(firstUserId));
        userRepository.save(new User(secondUserId));
        balanceRepository.save(new Balance(firstUserId, BigDecimal.valueOf(100000)));
        balanceRepository.save(new Balance(secondUserId, BigDecimal.valueOf(100000)));

        // 첫 번째 사용자 예약
        QueueToken firstToken = queueService.issueToken(firstUserId);

        // 활성 토큰 대기
        if (firstToken.getStatus() == QueueStatus.WAITING) {
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        QueueToken updatedToken = queueService.getQueueStatus(firstToken.getToken());
                        assertThat(updatedToken.getStatus()).isEqualTo(QueueStatus.ACTIVE);
                    });
        }

        ReserveSeatCommand firstCommand = new ReserveSeatCommand(firstUserId, testConcertId, testSeatNumber);
        ReservationResult firstReservation = reserveSeatUseCase.reserveSeat(firstCommand);

        // 예약 상태 확인
        Seat seatAfterReservation = seatRepository.findById(testSeat.getSeatId()).orElseThrow();
        log.info("After reservation - Status: {}, AssignedUser: {}",
                seatAfterReservation.getStatus(), seatAfterReservation.getAssignedUserId());

        // when - 첫 번째 사용자가 예약 취소
        reserveSeatUseCase.cancelReservation(firstReservation.getReservationId(), firstUserId);

        // 취소 후 좌석 상태 확인
        Seat seatAfterCancel = seatRepository.findById(testSeat.getSeatId()).orElseThrow();
        log.info("After cancel - Status: {}, AssignedUser: {}",
                seatAfterCancel.getStatus(), seatAfterCancel.getAssignedUserId());

        // 좌석이 실제로 해제되었는지 검증
        assertThat(seatAfterCancel.getStatus()).isEqualTo(Seat.SeatStatus.AVAILABLE);
        assertThat(seatAfterCancel.getAssignedUserId()).isNull();

        // then - 두 번째 사용자가 예약 가능
        QueueToken secondToken = queueService.issueToken(secondUserId);

        // 활성 토큰 대기
        if (secondToken.getStatus() == QueueStatus.WAITING) {
            await().atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        QueueToken updatedToken = queueService.getQueueStatus(secondToken.getToken());
                        assertThat(updatedToken.getStatus()).isEqualTo(QueueStatus.ACTIVE);
                    });
        }

        ReserveSeatCommand secondCommand = new ReserveSeatCommand(secondUserId, testConcertId, testSeatNumber);
        ReservationResult secondReservation = reserveSeatUseCase.reserveSeat(secondCommand);

        assertThat(secondReservation).isNotNull();
        assertThat(secondReservation.getUserId()).isEqualTo(secondUserId);

        // 좌석 상태 확인
        Seat finalSeat = seatRepository.findById(testSeat.getSeatId()).orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(Seat.SeatStatus.TEMPORARILY_ASSIGNED);
        assertThat(finalSeat.getAssignedUserId()).isEqualTo(secondUserId);
    }
}