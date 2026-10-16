package com.miniagent.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniagent.chat.ChatMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenEstimatorTest {

    private final TokenEstimator estimator = new TokenEstimator();

    @Test
    void longerTextEstimatesMoreTokens() {
        assertThat(estimator.estimate("abcdefghij")).isGreaterThan(estimator.estimate("ab"));
    }

    @Test
    void sumsMessageContents() {
        int total = estimator.estimateMessages(
                List.of(new ChatMessage("user", "abcd"), new ChatMessage("assistant", "efgh")));
        assertThat(total).isEqualTo(estimator.estimate("abcd") + estimator.estimate("efgh"));
    }
}
