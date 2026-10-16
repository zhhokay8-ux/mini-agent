package com.miniagent.rag;

import com.miniagent.rag.DocumentLoader.LoadedDocument;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 进程内向量库：启动时把文档切块并 embed，提问时做余弦 TopK。
 * 数据量很小所以用 List 线性扫描；上了 pgvector / Milvus 再换存储即可，检索接口可以保持 search。
 */
@Component
public class InMemoryKnowledgeStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryKnowledgeStore.class);

    private final RagProperties properties;
    private final DocumentLoader documentLoader;
    private final TextChunker chunker;
    private final EmbeddingClient embeddingClient;
    private final List<TextChunk> chunks = new ArrayList<>();

    public InMemoryKnowledgeStore(
            RagProperties properties,
            DocumentLoader documentLoader,
            TextChunker chunker,
            EmbeddingClient embeddingClient) {
        this.properties = properties;
        this.documentLoader = documentLoader;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
    }

    /**
     * 应用启动后建索引：加载文件 → 切块 → 编号 C1、C2… → 计算向量放进内存。
     * 之后改 knowledge 目录需要重启才会重新入库（本阶段不做热更新）。
     */
    @PostConstruct
    void index() {
        chunks.clear();
        int sequence = 1;
        for (LoadedDocument document : documentLoader.load(properties.knowledgePattern())) {
            for (String piece : chunker.split(document.text(), properties.chunkSize(), properties.chunkOverlap())) {
                String id = "C" + sequence++;
                chunks.add(new TextChunk(id, document.filename(), piece, embeddingClient.embed(piece)));
            }
        }
        log.info("Indexed {} chunks for RAG", chunks.size());
    }

    /**
     * 用与入库相同的 Embedding 把 query 变成向量，按余弦从高到低取 topK。
     * 不设分数阈值：即使不太相关也会返回 K 条，靠 Prompt 约束模型「没有就说不知道」。
     */
    public List<RagSource> search(String query, int topK) {
        float[] queryVector = embeddingClient.embed(query);
        return chunks.stream()
                .map(chunk -> new RagSource(
                        chunk.id(),
                        chunk.source(),
                        HashingEmbeddingClient.cosine(queryVector, chunk.embedding()),
                        chunk.text()))
                .sorted(Comparator.comparingDouble(RagSource::score).reversed())
                .limit(topK)
                .toList();
    }

    /** 测试或排障时查看当前库里有哪些块。 */
    List<TextChunk> chunks() {
        return List.copyOf(chunks);
    }
}
