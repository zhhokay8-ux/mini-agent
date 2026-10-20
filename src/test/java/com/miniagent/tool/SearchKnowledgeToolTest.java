package com.miniagent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniagent.rag.InMemoryKnowledgeStore;
import com.miniagent.rag.QueryRewriter;
import com.miniagent.rag.RagProperties;
import com.miniagent.rag.RagSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchKnowledgeToolTest {

    @Test
    void requiresQuery() throws Exception {
        SearchKnowledgeTool tool = new SearchKnowledgeTool(
                mock(InMemoryKnowledgeStore.class),
                new QueryRewriter(),
                new RagProperties(500, 50, 5, "classpath*:knowledge/**/*.{md,txt,pdf}"));
        String json = tool.execute(new ObjectMapper().readTree("{}"));
        assertThat(json).contains("query is required");
    }

    @Test
    void returnsChunksFromStore() throws Exception {
        InMemoryKnowledgeStore store = mock(InMemoryKnowledgeStore.class);
        when(store.search(anyString(), anyInt()))
                .thenReturn(List.of(new RagSource("C1", "return-policy.md", 0.9, "7 天无理由退货")));
        SearchKnowledgeTool tool = new SearchKnowledgeTool(
                store, new QueryRewriter(), new RagProperties(500, 50, 5, "classpath*:knowledge/**/*.{md,txt,pdf}"));
        String json = tool.execute(new ObjectMapper().readTree("{\"query\":\"退货政策是什么\"}"));
        assertThat(json).contains("\"ok\":true").contains("C1").contains("7 天无理由退货");
    }
}
