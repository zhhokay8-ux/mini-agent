package com.miniagent.rag;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** 按字符窗口切文档。这是最小策略，不是按段落/语义切。 */
@Component
public class TextChunker {

    /**
     * 把全文切成若干块。
     *
     * @param chunkSize 每块目标长度
     * @param overlap 下一块起点回退的字符数，形成重叠
     * @return 去掉过短碎片后的块列表；原文不超过 chunkSize 时整篇作为一块
     */
    public List<String> split(String text, int chunkSize, int overlap) {
        if (text == null) {
            return List.of();
        }
        String cleaned = text.replace("\r\n", "\n").trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        if (cleaned.length() <= chunkSize) {
            return List.of(cleaned);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < cleaned.length()) {
            int end = Math.min(start + chunkSize, cleaned.length());
            String piece = cleaned.substring(start, end).trim();
            if (piece.length() >= 20) {
                chunks.add(piece);
            }
            if (end == cleaned.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return List.copyOf(chunks);
    }
}
