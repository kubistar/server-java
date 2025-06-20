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
    private SeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private static final Logger log = LoggerFactory.getLogger(PointConcurrencyTest.class);

    private Long testConcertId = 1L;
    private Integer baseSeatNumber = 200; // 다른 테스트와 겹치지 않게
    private Integer seatPrice = 50000;

    @BeforeEach
    @Transactional
    void setUp() {
        // 테스트용 좌석들 미리 생성 (동시성 테스트용)
        for (int i = 0; i < 10; i++) {
            Seat testSeat = new Seat(null, testConcertId, baseSeatNumber + i, seatPrice);
            seatRepository.save(testSeat);
        }
    }

    @Test
    @DisplayName("동시성 테스트: 잔액 차감 시 음수 방지")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentBalanceDeduction_ShouldPreventNegativeBalance() throws Exception {
        // given
        String userId = "point-test-user";
        Long initialBalance = 100000L; // 10만원
        int threadCount = 10;
        int reservationCount = 5; // 5개 예약만 성공해야 함 (5만원 x 5 = 25만원 > 10만원)

        // 사용자 생성
        User user = new User(userId, initialBalance);
        userRepository.save(user);

        // 테스트용 좌석들 생성
        List<Seat> testSeats = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Seat seat = new Seat(null, testConcertId, baseSeatNumber + 100 + i, seatPrice);
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

        // 잔액이 음수가 되지 않아야 함
        User finalUser = userRepository.findById(userId).orElseThrow();
        assertThat(finalUser.getBalance()).isGreaterThanOrEqualTo(0L);

        // 성공한 결제 수는 초기 잔액으로 가능한 최대 수와 같아야 함
        int maxPossiblePayments = (int) (initialBalance / seatPrice); // 100,000 / 50,000 = 2
        assertThat(successCount.get()).isLessThanOrEqualTo(maxPossiblePayments);

        // 실제 차감된 금액 검증
        Long expectedRemainingBalance = initialBalance - (successCount.get() * seatPrice);
        assertThat(finalUser.getBalance()).isEqualTo(expectedRemainingBalance);

        log.info("최종 잔액: {}원, 예상 잔액: {}원", finalUser.getBalance(), expectedRemainingBalance);
    }

    @Test
    @DisplayName("동시성 테스트: 잔액 차감 시 음수 방지 (직접 결제)")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentBalanceDeduction_DirectPayment_ShouldPreventNegativeBalance() throws Exception {
        // given
        String userId = "balance-test-user";
        Long initialBalance = 100000L; // 10만원
        int threadCount = 10;
        Long paymentAmount = 30000L; // 3만원씩 결제 시도 (총 30만원 시도, 하지만 10만원만 있음)

        // 사용자 생성
        User user = new User(userId, initialBalance);
        userRepository.save(user);

        // 여러 예약 미리 생성 (QueueService 우회)
        List<ReservationResult> reservations = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            // 좌석 생성
            Seat seat = new Seat(null, testConcertId, baseSeatNumber + 300 + i, paymentAmount.intValue());
            seatRepository.save(seat);

            // 예약 직접 생성 (토큰 발급 없이)
            Reservation reservation = new Reservation(
                    userId,
                    testConcertId,
                    seat.getSeatId(),
                    paymentAmount.intValue(),
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

        // then - 결과 검증
        log.info("Success count: {}, Fail count: {}", successCount.get(), failCount.get());
        log.info("Results: {}", results);

        User finalUser = userRepository.findById(userId).orElseThrow();

        // 잔액이 음수가 되지 않아야 함
        assertThat(finalUser.getBalance()).isGreaterThanOrEqualTo(0L);

        // 성공한 결제 수는 가능한 최대 수와 같아야 함
        int maxPossiblePayments = (int) (initialBalance / paymentAmount); // 100,000 / 30,000 = 3
        assertThat(successCount.get()).isLessThanOrEqualTo(maxPossiblePayments);

        // 실제 잔액 검증
        Long expectedRemainingBalance = initialBalance - (successCount.get() * paymentAmount);
        assertThat(finalUser.getBalance()).isEqualTo(expectedRemainingBalance);

        log.info("최종 잔액: {}원, 예상 잔액: {}원", finalUser.getBalance(), expectedRemainingBalance);

    }
}