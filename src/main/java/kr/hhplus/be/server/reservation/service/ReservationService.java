package kr.hhplus.be.server.reservation.service;

import kr.hhplus.be.server.reservation.command.ReserveSeatCommand;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.reservation.dto.ReservationResult;
import kr.hhplus.be.server.reservation.repository.ReservationRepository;
import kr.hhplus.be.server.seat.repository.SeatRepository;
import kr.hhplus.be.server.common.lock.DistributedLockService;
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

    private static final int RESERVATION_TIMEOUT_MINUTES = 5;

    public ReservationService(
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            DistributedLockService distributedLockService) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.distributedLockService = distributedLockService;
    }

    @Override
    public ReservationResult reserveSeat(ReserveSeatCommand command) {
        String lockKey = generateSeatLockKey(command.getConcertId(), command.getSeatNumber());
        String lockValue = command.getUserId();

        // 분산 락 획득 시도
        if (!distributedLockService.tryLock(lockKey, lockValue, 10)) {
            throw new RuntimeException("다른 사용자가 처리 중입니다. 잠시 후 재시도해주세요.");
        }

        try {
            // 좌석 조회 및 상태 확인
            Seat seat = seatRepository.findByConcertIdAndSeatNumber(
                    command.getConcertId(),
                    command.getSeatNumber()
            ).orElseThrow(() -> new RuntimeException("존재하지 않는 좌석입니다."));

            // 좌석 가용성 검증
            if (!seat.isAvailable()) {
                if (seat.isExpired()) {
                    // 만료된 임시 배정 해제
                    seat.releaseAssignment();
                    seatRepository.save(seat);
                } else {
                    throw new RuntimeException("이미 다른 사용자가 선택한 좌석입니다.");
                }
            }

            // 임시 배정 처리
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(RESERVATION_TIMEOUT_MINUTES);
            seat.assignTemporarily(command.getUserId(), expiresAt);
            seatRepository.save(seat);

            // 예약 생성
            Reservation reservation = new Reservation(
                    command.getUserId(),
                    command.getConcertId(),
                    seat.getSeatId(),
                    seat.getPrice(),
                    expiresAt
            );
            reservationRepository.save(reservation);

            return new ReservationResult(reservation, seat.getSeatNumber());

        } finally {
            // 분산 락 해제
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

        // 예약 취소
        reservation.cancel();
        reservationRepository.save(reservation);

        // 좌석 해제
        seat.releaseAssignment();
        seatRepository.save(seat);
    }

    @Override
    public void releaseExpiredReservations() {
        // 만료된 예약 조회
        List<Reservation> expiredReservations = reservationRepository.findExpiredReservations();

        for (Reservation reservation : expiredReservations) {
            // 예약 만료 처리
            reservation.expire();

            // 좌석 해제
            Seat seat = seatRepository.findById(reservation.getSeatId())
                    .orElseThrow(() -> new RuntimeException("좌석 정보를 찾을 수 없습니다."));
            seat.releaseAssignment();
            seatRepository.save(seat);
        }

        // 일괄 저장
        reservationRepository.saveAll(expiredReservations);
    }

    private String generateSeatLockKey(Long concertId, Integer seatNumber) {
        return String.format("seat_lock:%d:%d", concertId, seatNumber);
    }
}