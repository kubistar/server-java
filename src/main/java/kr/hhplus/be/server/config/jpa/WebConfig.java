package kr.hhplus.be.server.config.jpa;

import kr.hhplus.be.server.interceptor.QueueTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 설정 클래스
 * 대기열 토큰 검증 인터셉터 설정
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final QueueTokenInterceptor queueTokenInterceptor;

    public WebConfig(QueueTokenInterceptor queueTokenInterceptor) {
        this.queueTokenInterceptor = queueTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(queueTokenInterceptor)
                .addPathPatterns("/api/concerts/*/seats")  // 좌석 조회 시 토큰 필요
                .addPathPatterns("/api/reservations/**")   // 예약 관련 API
                .addPathPatterns("/api/payments/**")       // 결제 관련 API
                .addPathPatterns("/api/users/*/balance")   // 잔액 충전 API
                .excludePathPatterns("/api/queue/**")      // 대기열 관련 API는 제외
                .excludePathPatterns("/api/concerts/available-dates")  // 콘서트 목록은 제외
                .excludePathPatterns("/api/concerts/*/")   // 콘서트 상세는 제외
                .excludePathPatterns("/api/concerts/by-date")  // 날짜별 조회 제외
                .excludePathPatterns("/api/concerts/search")   // 검색 제외
                .excludePathPatterns("/swagger-ui/**")     // Swagger 제외
                .excludePathPatterns("/v3/api-docs/**")    // API 문서 제외
                .excludePathPatterns("/actuator/**");      // Actuator 제외
    }
}
