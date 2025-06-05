package kr.hhplus.be.server.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 대기열 토큰 관리 API (Mock 버전)
 *
 * 🚧 현재는 Mock 데이터만 리턴합니다.
 * 실제 비즈니스 로직은 추후 구현 예정입니다.
 */
@RestController
@RequestMapping("/api/queue")
@Tag(name = "🎫 대기열 관리", description = "대기열 토큰 발급 및 상태 조회")
public class QueueController {

    /**
     * 🎫 대기열 토큰 발급 (Mock)
     */
    @PostMapping("/token")
    @Operation(summary = "대기열 토큰 발급", description = "대기열 진입용 토큰을 발급받습니다.")
    @ApiResponse(responseCode = "201", description = "토큰 발급 성공")
    public ResponseEntity<Map<String, Object>> issueToken(@RequestBody Map<String, String> request) {

        // 🎭 Mock 데이터 생성
        String userId = request.get("userId");
        String mockToken = UUID.randomUUID().toString();

        // 📦 응답 데이터 구성
        Map<String, Object> data = new HashMap<>();
        data.put("token", mockToken);
        data.put("userId", userId);
        data.put("queuePosition", 150);  // 가짜 대기 순서
        data.put("estimatedWaitTimeMinutes", 15);  // 가짜 예상 시간
        data.put("status", "WAITING");
        data.put("issuedAt", LocalDateTime.now().toString());
        data.put("expiresAt", LocalDateTime.now().plusHours(1).toString());

        // 📋 최종 응답
        Map<String, Object> response = new HashMap<>();
        response.put("code", 201);
        response.put("data", data);
        response.put("message", "대기열 토큰이 발급되었습니다. (Mock)");

        return ResponseEntity.status(201).body(response);
    }

    /**
     * 📊 대기열 상태 조회 (Mock)
     */
    @GetMapping("/status")
    @Operation(
            summary = "대기열 상태 조회",
            description = "현재 대기열 상태를 확인합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<Map<String, Object>> getQueueStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        // 🎭 Mock 상태 데이터
        Map<String, Object> data = new HashMap<>();
        data.put("token", "550e8400-e29b-41d4-a716-446655440000");
        data.put("userId", "user-123");
        data.put("status", "WAITING");
        data.put("queuePosition", 120);  // 가짜 현재 순서
        data.put("estimatedWaitTimeMinutes", 12);  // 가짜 남은 시간
        data.put("totalInQueue", 1500);  // 가짜 전체 대기자
        data.put("activeUsers", 100);
        data.put("maxActiveUsers", 200);

        // 📋 응답 구성
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", data);
        response.put("message", "대기열 상태 조회 성공 (Mock)");

        return ResponseEntity.ok(response);
    }

    /**
     * 🧪 Mock API 테스트용 엔드포인트
     */
    @GetMapping("/test")
    @Operation(summary = "API 테스트", description = "Swagger가 정상 작동하는지 확인용")
    @ApiResponse(responseCode = "200", description = "테스트 성공")
    public ResponseEntity<Map<String, Object>> testApi() {

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "🎉 Swagger Mock API가 정상 작동합니다!");
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }
}