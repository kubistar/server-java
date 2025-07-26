package kr.hhplus.be.server.queue.event;

import kr.hhplus.be.server.common.event.BaseEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserActivatedEvent extends BaseEvent {

    private String userId;
    private String token;
    private String sessionId;
    private Long previousPosition;

    public UserActivatedEvent(String userId, String token, String sessionId, Long previousPosition) {
        super("USER_ACTIVATED");
        this.userId = userId;
        this.token = token;
        this.sessionId = sessionId;
        this.previousPosition = previousPosition;
    }
}