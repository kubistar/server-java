package kr.hhplus.be.server.seat;

import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatJpaRepository;
import kr.hhplus.be.server.seat.repository.SeatRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SeatRepositoryImplTest {

    @Mock
    private SeatJpaRepository seatJpaRepository;

    @InjectMocks
    private SeatRepositoryImpl seatRepository;

    @Test
    @DisplayName("콘서트 ID와 좌석 번호로 좌석 조회")
    void whenFindByConcertIdAndSeatNumber_ThenShouldReturnSeat() {
        // given
        Long concertId = 1L;
        Integer seatNumber = 15;
        Seat expectedSeat = new Seat(concertId, seatNumber, BigDecimal.valueOf(50000));

        given(seatJpaRepository.findByConcertIdAndSeatNumber(concertId, seatNumber))
                .willReturn(Optional.of(expectedSeat));

        // when
        Optional<Seat> result = seatRepository.findByConcertIdAndSeatNumber(concertId, seatNumber);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(expectedSeat);
    }

    @Test
    @DisplayName("만료된 임시 배정 좌석들 조회")
    void whenFindExpiredTemporaryAssignments_ThenShouldReturnExpiredSeats() {
        // given
        Seat expiredSeat1 = new Seat(1L, 15, BigDecimal.valueOf(50000));
        Seat expiredSeat2 = new Seat(1L, 16, BigDecimal.valueOf(50000));
        List<Seat> expiredSeats = Arrays.asList(expiredSeat1, expiredSeat2);

        // 새로운 메서드에 맞는 Mock 설정
        given(seatJpaRepository.findByStatusAndAssignedUntilBefore(
                eq(Seat.SeatStatus.TEMPORARILY_ASSIGNED),
                any(LocalDateTime.class)
        )).willReturn(expiredSeats);

        // when
        List<Seat> result = seatRepository.findExpiredTemporaryAssignments();

        // then
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(expiredSeat1, expiredSeat2);

        // Mock 호출 검증
        verify(seatJpaRepository).findByStatusAndAssignedUntilBefore(
                eq(Seat.SeatStatus.TEMPORARILY_ASSIGNED),
                any(LocalDateTime.class)
        );
    }
}