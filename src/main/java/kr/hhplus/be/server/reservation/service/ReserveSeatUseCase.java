package kr.hhplus.be.server.reservation.service;

import kr.hhplus.be.server.reservation.command.ReserveSeatCommand;
import kr.hhplus.be.server.reservation.dto.ReservationResult;

public interface ReserveSeatUseCase {
    ReservationResult reserveSeat(ReserveSeatCommand command);
    ReservationResult getReservationStatus(String reservationId);
    void cancelReservation(String reservationId, String userId);
    void releaseExpiredReservations();
}
