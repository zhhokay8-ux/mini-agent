package com.miniagent.web;

import com.miniagent.session.SessionMemoryService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionMemoryService sessions;

    public SessionController(SessionMemoryService sessions) {
        this.sessions = sessions;
    }

    /** 开一个新会话，后续 /api/chat、/api/rag 把返回的 id 放进 sessionId。 */
    @PostMapping
    public Map<String, String> create() {
        return Map.of("sessionId", sessions.create());
    }
}
