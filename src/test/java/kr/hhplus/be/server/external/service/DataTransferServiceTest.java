package kr.hhplus.be.server.external.service;

import kr.hhplus.be.server.external.client.DataPlatformClient;
import kr.hhplus.be.server.external.dto.ReservationDataDto;
import kr.hhplus.be.server.reservation.event.ReservationCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataTransferServiceTest {

    @Mock
    private DataPlatformClient dataPlatformClient;

    @InjectMocks
    private DataTransferService dataTransferService;

    @Test
    void 예약_완료_이벤트_수신_시_데이터_플랫폼으로_전송한다() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        BigDecimal price = new BigDecimal("50000"); // ✅ BigDecimal 사용

        ReservationCompletedEvent event = new ReservationCompletedEvent(
                "reservation123",
                "user123",
                1L,
                100L,
                10,
                price, // BigDecimal 타입
                now
        );

        // When
        dataTransferService.handleReservationCompleted(event);

        // Then
        ArgumentCaptor<ReservationDataDto> captor = ArgumentCaptor.forClass(ReservationDataDto.class);
        verify(dataPlatformClient).sendReservationData(captor.capture());

        ReservationDataDto captured = captor.getValue();
        assertThat(captured.reservationId()).isEqualTo("reservation123");
        assertThat(captured.userId()).isEqualTo("user123");
        assertThat(captured.concertId()).isEqualTo(1L);
        assertThat(captured.seatNumber()).isEqualTo(10);
        assertThat(captured.price()).isEqualTo(price);
        assertThat(captured.reservedAt()).isEqualTo(now);
    }

    @Test
    void 데이터_전송_실패_시_예외를_로깅하고_재시도하지_않는다() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        BigDecimal price = new BigDecimal("50000"); // ✅ BigDecimal 사용

        ReservationCompletedEvent event = new ReservationCompletedEvent(
                "reservation123",
                "user123",
                1L,
                100L,
                10,
                price,
                now
        );

        doThrow(new RuntimeException("네트워크 오류"))
                .when(dataPlatformClient).sendReservationData(any());

        // When & Then
        dataTransferService.handleReservationCompleted(event);

        // 예외가 발생해도 메서드가 정상적으로 완료되어야 함
        verify(dataPlatformClient).sendReservationData(any());
    }
}
