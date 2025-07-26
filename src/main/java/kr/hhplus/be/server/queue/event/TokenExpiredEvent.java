package kr.hhplus.be.server.queue.event;

import kr.hhplus.be.server.common.event.BaseEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TokenExpiredEvent extends BaseEvent {

    private String userId;
    private String token;
    private String expireReason;

    public TokenExpiredEvent(String userId, String token, String expireReason) {
        super("TOKEN_EXPIRED");
        this.userId = userId;
        this.token = token;
        this.expireReason = expireReason;
    }
}