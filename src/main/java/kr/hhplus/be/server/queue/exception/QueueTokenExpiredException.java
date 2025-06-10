package kr.hhplus.be.server.queue.exception;

public class QueueTokenExpiredException extends RuntimeException {
    public QueueTokenExpiredException(String message) {
        super(message);
    }
}