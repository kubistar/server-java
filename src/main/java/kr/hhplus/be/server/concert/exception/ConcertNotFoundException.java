package kr.hhplus.be.server.concert.exception;

/**
 * 콘서트를 찾을 수 없을 때 발생하는 예외
 *
 * 다음과 같은 상황에서 발생:
 * - 존재하지 않는 콘서트 ID로 조회 시
 * - 삭제된 콘서트에 접근 시
 * - 권한이 없는 콘서트에 접근 시
 *
 * HTTP 404 Not Found 응답으로 처리됨
 */
public class ConcertNotFoundException extends RuntimeException {

    /**
     * 콘서트 찾기 실패 예외 생성
     * @param message 에러 메시지 (예: "콘서트를 찾을 수 없습니다: {concertId}")
     */
    public ConcertNotFoundException(String message) {
        super(message);
    }
}