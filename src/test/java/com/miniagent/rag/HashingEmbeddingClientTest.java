package com.miniagent.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HashingEmbeddingClientTest {

    private final HashingEmbeddingClient client = new HashingEmbeddingClient();

    @Test
    void identicalTextHasCosineOne() {
        float[] vector = client.embed("差旅费报销流程");
        assertThat(HashingEmbeddingClient.cosine(vector, vector)).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(1e-5f));
    }

    @Test
    void similarQueriesRankCloserThanUnrelated() {
        float[] reimburse = client.embed("差旅费报销申请流程");
        float[] similar = client.embed("出差报销标准和发票");
        float[] unrelated = client.embed("股权激励归属时间表");
        assertThat(HashingEmbeddingClient.cosine(reimburse, similar))
                .isGreaterThan(HashingEmbeddingClient.cosine(reimburse, unrelated));
    }
}
