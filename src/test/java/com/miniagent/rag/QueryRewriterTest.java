package com.miniagent.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QueryRewriterTest {

    private final QueryRewriter rewriter = new QueryRewriter();

    @Test
    void rewritesColloquialReimbursement() {
        assertThat(rewriter.rewrite("报销咋整")).isEqualTo("差旅费报销流程");
    }

    @Test
    void keepsUnknownQuestion() {
        assertThat(rewriter.rewrite("年终奖怎么发")).isEqualTo("年终奖怎么发");
    }
}
