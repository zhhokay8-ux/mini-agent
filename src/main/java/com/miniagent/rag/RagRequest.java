package com.miniagent.rag;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /api/rag} 入参。 */
public record RagRequest(@NotBlank String question) {}
