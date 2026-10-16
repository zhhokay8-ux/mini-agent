package com.miniagent.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionWindowTest {

    @Test
    void recentKeepsTail() {
        List<SessionMessageEntity> all = messages(5);
        List<SessionMessageEntity> recent = SessionWindow.recent(all, 4);
        assertThat(recent).hasSize(4);
        assertThat(recent.get(0).getContent()).isEqualTo("m1");
    }

    @Test
    void staleSkipsAlreadyCompacted() {
        List<SessionMessageEntity> all = messages(6);
        all.get(0).setCompacted(true);
        List<SessionMessageEntity> stale = SessionWindow.staleUncompacted(all, 4);
        assertThat(stale).hasSize(1);
        assertThat(stale.get(0).getContent()).isEqualTo("m1");
    }

    @Test
    void followUpDetectsApplyAfterThat() {
        assertThat(SessionWindow.isFollowUp("那怎么申请")).isTrue();
        assertThat(SessionWindow.isFollowUp("怎么请假")).isFalse();
    }

    private static List<SessionMessageEntity> messages(int count) {
        List<SessionMessageEntity> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new SessionMessageEntity("s", "user", "m" + i, Instant.now()));
        }
        return list;
    }
}
