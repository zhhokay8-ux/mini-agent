package com.miniagent.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    @Test
    void splitsWithOverlap() {
        String text = "a".repeat(120);
        List<String> chunks = chunker.split(text, 50, 10);
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.get(0)).hasSize(50);
    }

    @Test
    void keepsShortDocumentWhole() {
        assertThat(chunker.split("短文档内容足够成一块", 500, 50)).containsExactly("短文档内容足够成一块");
    }
}
