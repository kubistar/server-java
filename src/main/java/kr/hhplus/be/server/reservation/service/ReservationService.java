package kr.hhplus.be.server.reservation.service;

import kr.hhplus.be.server.common.lock.DistributedLockService;
import kr.hhplus.be.server.reservation.command.ReserveSeatCommand;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.dto.ReservationResult;
import kr.hhplus.be.server.reservation.event.ReservationCompletedEvent;
import kr.hhplus.be.server.reservation.repository.ReservationRepository;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ReservationService implements ReserveSeatUseCase {

    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final DistributedLockService distributedLockService;
    private final ApplicationEventPublisher eventPublisher;

    private static final int RESERVATION_TIMEOUT_MINUTES = 5;

    public ReservationService(
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            DistributedLockService distributedLockService,
            ApplicationEventPublisher eventPublisher) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.distributedLockService = distributedLockService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ReservationResult reserveSeat(ReserveSeatCommand command) {
        String lockKey = generateSeatLockKey(command.getConcertId(), command.getSeatNumber());
        String lockValue = command.getUserId();

        if (!distributedLockService.tryLock(lockKey, lockValue, 10)) {
            throw new RuntimeException("다른 사용자가 처리 중입니다. 잠시 후 재시도해주세요.");
        }

        try {
            Seat seat = seatRepository.findByConcertIdAndSeatNumberWithLock(
                    command.getConcertId(),
                    command.getSeatNumber()
            ).orElseThrow(() -> new RuntimeException("존재하지 않는 좌석입니다."));

            if (!seat.isAvailable()) {
                if (seat.isExpired()) {
                    seat.releaseAssignment();
                    seatRepository.save(seat);
                } else {
                    throw new RuntimeException("이미 다른 사용자가 선택한 좌석입니다.");
                }
            }

            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(RESERVATION_TIMEOUT_MINUTES);
            seat.assignTemporarily(command.getUserId(), expiresAt);
            seatRepository.save(seat);

            Reservation reservation = new Reservation(
                    command.getUserId(),
                    command.getConcertId(),
                    seat.getSeatId(),
                    seat.getPrice(),
                    expiresAt
            );
            reservationRepository.save(reservation);

            // 이벤트 발행 - 트랜잭션 커밋 후 처리됨
            ReservationCompletedEvent event = new ReservationCompletedEvent(
                    reservation.getReservationId(),
                    reservation.getUserId(),
                    reservation.getConcertId(),
                    seat.getSeatId(),
                    seat.getSeatNumber(),
                    seat.getPrice(),
                    LocalDateTime.now()
            );
            eventPublisher.publishEvent(event);

            return new ReservationResult(reservation, seat.getSeatNumber());

        } finally {
            distributedLockService.unlock(lockKey, lockValue);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResult getReservationStatus(String reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 예약입니다."));

        Seat seat = seatRepository.findById(reservation.getSeatId())
                .orElseThrow(() -> new RuntimeException("좌석 정보를 찾을 수 없습니다."));

        return new ReservationResult(reservation, seat.getSeatNumber());
    }

    @Override
    public void cancelReservation(String reservationId, String userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 예약입니다."));

        if (!reservation.getUserId().equals(userId)) {
            throw new RuntimeException("예약을 취소할 권한이 없습니다.");
        }

        Seat seat = seatRepository.findById(reservation.getSeatId())
                .orElseThrow(() -> new RuntimeException("좌석 정보를 찾을 수 없습니다."));

        reservation.cancel();
        reservationRepository.save(reservation);

        seat.releaseAssignment();
        seatRepository.save(seat);
    }

    @Override
    public void releaseExpiredReservations() {
        List<Reservation> expiredReservations = reservationRepository.findByStatusAndExpiresAtBefore(
                Reservation.ReservationStatus.TEMPORARILY_ASSIGNED,
                LocalDateTime.now()
        );

        for (Reservation reservation : expiredReservations) {
            reservation.expire();

            Seat seat = seatRepository.findById(reservation.getSeatId())
                    .orElseThrow(() -> new RuntimeException("좌석 정보를 찾을 수 없습니다."));
            seat.releaseAssignment();
            seatRepository.save(seat);
        }

        reservationRepository.saveAll(expiredReservations);
    }

    private String generateSeatLockKey(Long concertId, Integer seatNumber) {
        return String.format("seat_lock:%d:%d", concertId, seatNumber);
    }
}