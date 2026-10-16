package com.miniagent.session;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionMessageRepository extends JpaRepository<SessionMessageEntity, Long> {

    List<SessionMessageEntity> findBySessionIdOrderByIdAsc(String sessionId);
}
