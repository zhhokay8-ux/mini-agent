package com.miniagent.web;

import com.miniagent.rag.RagRequest;
import com.miniagent.rag.RagResponse;
import com.miniagent.rag.RagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /** 最小 RAG 入口：入参只有 question，返回答案、改写后的检索词和 sources。 */
    @PostMapping
    public RagResponse ask(@Valid @RequestBody RagRequest request) {
        return ragService.ask(request.question());
    }
}
