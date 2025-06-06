package kr.hhplus.be.server.service;

import kr.hhplus.be.server.command.ReserveSeatCommand;
import kr.hhplus.be.server.dto.ReservationResult;

public interface ReserveSeatUseCase {
    ReservationResult reserveSeat(ReserveSeatCommand command);
    ReservationResult getReservationStatus(String reservationId);
    void cancelReservation(String reservationId, String userId);
    void releaseExpiredReservations();
}
