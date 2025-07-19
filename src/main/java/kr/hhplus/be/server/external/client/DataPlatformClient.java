package kr.hhplus.be.server.external.client;

import kr.hhplus.be.server.external.dto.ReservationDataDto;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class DataPlatformClient {

    private final RestTemplate restTemplate;

    public DataPlatformClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void sendReservationData(ReservationDataDto data) {
        try {
            // Mock API 호출 (실제로는 외부 API URL)
            String mockApiUrl = "https://mock-data-platform.com/api/reservations";

            Map<String, Object> payload = Map.of(
                    "reservationId", data.reservationId(),
                    "userId", data.userId(),
                    "concertId", data.concertId(),
                    "seatNumber", data.seatNumber(),
                    "price", data.price(),
                    "timestamp", data.reservedAt().toString()
            );
            restTemplate.postForObject(mockApiUrl, payload, String.class);

        } catch (Exception e) {
            // 로깅 처리
            throw new RuntimeException("데이터 플랫폼 전송 실패", e);
        }
    }
}
