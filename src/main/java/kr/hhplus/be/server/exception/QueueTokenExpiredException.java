package kr.hhplus.be.server.exception;

public class QueueTokenExpiredException extends RuntimeException {
    public QueueTokenExpiredException(String message) {
        super(message);
    }
}