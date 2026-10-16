package com.miniagent.session;

/** 请求带了 sessionId，但库里没有对应会话。 */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(String sessionId) {
        super("session not found: " + sessionId);
    }
}
