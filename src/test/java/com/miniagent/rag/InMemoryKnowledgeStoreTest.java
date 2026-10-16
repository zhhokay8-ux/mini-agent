package com.miniagent.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.miniagent.rag.DocumentLoader.LoadedDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryKnowledgeStoreTest {

    @Test
    void retrievesReimbursementChunk() {
        DocumentLoader loader = mock(DocumentLoader.class);
        when(loader.load(anyString()))
                .thenReturn(List.of(new LoadedDocument(
                        "travel-reimbursement.md",
                        "差旅费报销须在返回后 7 个自然日内提交。高铁二等座可实报实销。禁止报销景点门票。")));
        RagProperties properties = new RagProperties(500, 50, 5, "classpath*:knowledge/**/*.{md,txt,pdf}");
        InMemoryKnowledgeStore store =
                new InMemoryKnowledgeStore(properties, loader, new TextChunker(), new HashingEmbeddingClient());
        store.index();

        List<RagSource> hits = store.search("差旅费报销流程", 3);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).text()).contains("报销");
    }
}
