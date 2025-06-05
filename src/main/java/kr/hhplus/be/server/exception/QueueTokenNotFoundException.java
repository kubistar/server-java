package kr.hhplus.be.server.exception;

public class QueueTokenNotFoundException extends RuntimeException {
    public QueueTokenNotFoundException(String message) {
        super(message);
    }
}