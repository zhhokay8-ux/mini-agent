package com.miniagent.rag;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/** 从 classpath（或其它 Spring Resource）读出知识文件的纯文本，供后续切块。 */
@Component
public class DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(DocumentLoader.class);

    private final ResourcePatternResolver resources;
    private final Tika tika = new Tika();

    public DocumentLoader(ResourcePatternResolver resources) {
        this.resources = resources;
    }

    /**
     * 按通配符加载全部可读文件。md/txt 当 UTF-8 文本；pdf 交给 Tika 抽字。
     * 空文件会跳过，避免切出无意义块。
     */
    public List<LoadedDocument> load(String pattern) {
        try {
            Resource[] files = resources.getResources(pattern);
            List<LoadedDocument> documents = new ArrayList<>();
            for (Resource file : files) {
                if (!file.isReadable() || file.getFilename() == null) {
                    continue;
                }
                String filename = file.getFilename();
                String text = read(file, filename);
                if (text == null || text.isBlank()) {
                    log.warn("Skip empty knowledge file {}", filename);
                    continue;
                }
                documents.add(new LoadedDocument(filename, text));
            }
            log.info("Loaded {} knowledge documents", documents.size());
            return List.copyOf(documents);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load knowledge files from " + pattern, ex);
        }
    }

    /** 按扩展名选择解析方式：PDF 走 Tika，其它按纯文本读入。 */
    private String read(Resource file, String filename) throws Exception {
        String lower = filename.toLowerCase(Locale.ROOT);
        try (InputStream input = file.getInputStream()) {
            if (lower.endsWith(".pdf")) {
                return tika.parseToString(input);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 一份已抽出正文的知识文件。 */
    public record LoadedDocument(String filename, String text) {}
}
