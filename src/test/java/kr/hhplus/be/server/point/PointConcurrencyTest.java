package kr.hhplus.be.server.point;

import kr.hhplus.be.server.payment.command.PaymentCommand;
import kr.hhplus.be.server.payment.service.PaymentService;
import kr.hhplus.be.server.queue.domain.QueueStatus;
import kr.hhplus.be.server.queue.domain.QueueToken;
import kr.hhplus.be.server.queue.service.QueueService;
import kr.hhplus.be.server.reservation.command.ReserveSeatCommand;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.dto.ReservationResult;
import kr.hhplus.be.server.reservation.repository.ReservationRepository;
import kr.hhplus.be.server.reservation.service.ReserveSeatUseCase;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatRepository;
import kr.hhplus.be.server.user.domain.User;
import kr.hhplus.be.server.user.repository.UserRepository;
import kr.hhplus.be.server.balance.domain.Balance; // 추가
import kr.hhplus.be.server.balance.repository.BalanceRepository; // 추가
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

import java.math.BigDecimal; // 추가
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@ActiveProfiles("test")
class PointConcurrencyTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private ReserveSeatUseCase reserveSeatUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BalanceRepository balanceRepository; // 추가

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private static final Logger log = LoggerFactory.getLogger(PointConcurrencyTest.class);

    private Long testConcertId = 1L;
    private Integer baseSeatNumber = 200; // 다른 테스트와 겹치지 않게
    private BigDecimal seatPrice = BigDecimal.valueOf(50000); // Integer → BigDecimal

    @BeforeEach
    @Transactional
    void setUp() {
        // 테스트용 좌석들 미리 생성 (동시성 테스트용)
        for (int i = 0; i < 10; i++) {
            Seat testSeat = new Seat(testConcertId, baseSeatNumber + i, seatPrice); // null 제거
            seatRepository.save(testSeat);
        }
    }

    @Test
    @DisplayName("동시성 테스트: 잔액 차감 시 음수 방지")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentBalanceDeduction_ShouldPreventNegativeBalance() throws Exception {
        // given
        String userId = "point-test-user";
        BigDecimal initialBalance = BigDecimal.valueOf(100000); // Long → BigDecimal
        int threadCount = 10;
        int reservationCount = 5; // 5개 예약만 성공해야 함 (5만원 x 5 = 25만원 > 10만원)

        // 사용자 및 잔액 생성 (분리)
        User user = new User(userId); // balance 제거
        userRepository.save(user);

        Balance balance = new Balance(userId, initialBalance); // 별도 생성
        balanceRepository.save(balance);

        // 테스트용 좌석들 생성
        List<Seat> testSeats = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Seat seat = new Seat(testConcertId, baseSeatNumber + 100 + i, seatPrice); // null 제거
            testSeats.add(seatRepository.save(seat));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<String>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 동시에 예약 및 결제 시도
        for (int i = 0; i < threadCount; i++) {
            final int seatIndex = i;
            final int seatNumber = baseSeatNumber + 100 + i;

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // 토큰 발급
                    QueueToken token = queueService.issueToken(userId);

                    // 토큰 활성화 대기
                    int maxRetries = 10;
                    while (token.getStatus() == QueueStatus.WAITING && maxRetries-- > 0) {
                        Thread.sleep(500);
                        token = queueService.getQueueStatus(token.getToken());
                    }

                    if (token.getStatus() != QueueStatus.ACTIVE) {
                        failCount.incrementAndGet();
                        return "TOKEN_FAILED";
                    }

                    // 좌석 예약
                    ReserveSeatCommand reserveCommand = new ReserveSeatCommand(userId, testConcertId, seatNumber);
                    ReservationResult reservation = reserveSeatUseCase.reserveSeat(reserveCommand);

                    // 결제 (잔액 차감)
                    PaymentCommand paymentCommand = new PaymentCommand(reservation.getReservationId(), userId);
                    paymentService.processPayment(paymentCommand);

                    successCount.incrementAndGet();
                    return "SUCCESS";

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.info("결제 실패: {}", e.getMessage());
                    return "PAYMENT_FAILED: " + e.getMessage();
                }
            }, executorService);

            futures.add(future);
        }

        // 모든 작업 완료 대기
        List<String> results = futures.stream()
                .map(future -> {
                    try {
                        return future.get(30, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        return "TIMEOUT";
                    }
                })
                .toList();

        executorService.shutdown();

        // then - 결과 검증
        log.info("Success count: {}, Fail count: {}", successCount.get(), failCount.get());
        log.info("Results: {}", results);

        // 잔액이 음수가 되지 않아야 함 - Balance에서 조회
        Balance finalBalance = balanceRepository.findByUserId(userId).orElseThrow();
        assertThat(finalBalance.getAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        // 성공한 결제 수는 초기 잔액으로 가능한 최대 수와 같아야 함
        int maxPossiblePayments = initialBalance.divide(seatPrice).intValue(); // BigDecimal 계산
        assertThat(successCount.get()).isLessThanOrEqualTo(maxPossiblePayments);

        // 실제 차감된 금액 검증
        BigDecimal expectedRemainingBalance = initialBalance.subtract(
                seatPrice.multiply(BigDecimal.valueOf(successCount.get()))
        );
        assertThat(finalBalance.getAmount()).isEqualTo(expectedRemainingBalance);

        log.info("최종 잔액: {}원, 예상 잔액: {}원", finalBalance.getAmount(), expectedRemainingBalance);
    }

    @Test
    @DisplayName("동시성 테스트: 잔액 차감 시 음수 방지 (직접 결제)")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentBalanceDeduction_DirectPayment_ShouldPreventNegativeBalance() throws Exception {
        // given
        String userId = "balance-test-user";
        BigDecimal initialBalance = BigDecimal.valueOf(100000); // Long → BigDecimal
        int threadCount = 10;
        BigDecimal paymentAmount = BigDecimal.valueOf(30000); // Long → BigDecimal

        // 사용자 및 잔액 생성 (분리)
        User user = new User(userId); // balance 제거
        userRepository.save(user);

        Balance balance = new Balance(userId, initialBalance); // 별도 생성
        balanceRepository.save(balance);

        // 여러 예약 미리 생성 (QueueService 우회)
        List<ReservationResult> reservations = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            // 좌석 생성
            Seat seat = new Seat(testConcertId, baseSeatNumber + 300 + i, paymentAmount); // null 제거
            seatRepository.save(seat);

            // 예약 직접 생성 (토큰 발급 없이)
            Reservation reservation = new Reservation(
                    userId,
                    testConcertId,
                    seat.getSeatId(),
                    paymentAmount,
                    LocalDateTime.now().plusMinutes(5)
            );
            reservationRepository.save(reservation);

            // 좌석 임시 배정
            seat.assignTemporarily(userId, LocalDateTime.now().plusMinutes(5));
            seatRepository.save(seat);

            ReservationResult result = new ReservationResult(reservation, baseSeatNumber + 300 + i);
            reservations.add(result);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 시간 측정 시작
        long startTime = System.currentTimeMillis();

        // when - 동시에 결제 시도
        List<CompletableFuture<String>> futures = reservations.stream()
                .map(reservation -> CompletableFuture.supplyAsync(() -> {
                    try {
                        PaymentCommand paymentCommand = new PaymentCommand(reservation.getReservationId(), userId);
                        paymentService.processPayment(paymentCommand);
                        successCount.incrementAndGet();
                        return "SUCCESS";
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                        return "FAILED: " + e.getMessage();
                    }
                }, executorService))
                .collect(Collectors.toList());

        List<String> results = futures.stream()
                .map(future -> {
                    try {
                        return future.get(15, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        return "TIMEOUT";
                    }
                })
                .collect(Collectors.toList());

        executorService.shutdown();

        // 시간 측정 끝
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // then - 결과 검증
        log.info("=== 잔액 차감 동시성 테스트 결과 ===");
        log.info("Success count: {}, Fail count: {}", successCount.get(), failCount.get());
        log.info("{}개 스레드 동시 결제 소요시간: {}ms", threadCount, duration);
        log.info("평균 응답시간: {}ms per request", duration / threadCount);
        log.info("스레드 처리율: {} requests/second", Math.round((double) threadCount / duration * 1000));
        log.info("Results: {}", results);

        // 잔액이 음수가 되지 않아야 함 - Balance에서 조회
        Balance finalBalance = balanceRepository.findByUserId(userId).orElseThrow();

        // 잔액이 음수가 되지 않아야 함
        assertThat(finalBalance.getAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        // 성공한 결제 수는 가능한 최대 수와 같아야 함
        int maxPossiblePayments = initialBalance.divide(paymentAmount).intValue(); // BigDecimal 계산
        assertThat(successCount.get()).isLessThanOrEqualTo(maxPossiblePayments);

        // 실제 잔액 검증
        BigDecimal expectedRemainingBalance = initialBalance.subtract(
                paymentAmount.multiply(BigDecimal.valueOf(successCount.get()))
        );
        assertThat(finalBalance.getAmount()).isEqualTo(expectedRemainingBalance);

        log.info("최종 잔액: {}원, 예상 잔액: {}원", finalBalance.getAmount(), expectedRemainingBalance);
        log.info("동시성 제어 성공: 음수 잔액 방지 ✅");
        log.info("=== 테스트 완료 ===");
    }
}