package kr.hhplus.be.server.queue.exception;

public class QueueTokenNotFoundException extends RuntimeException {
    public QueueTokenNotFoundException(String message) {
        super(message);
    }
}