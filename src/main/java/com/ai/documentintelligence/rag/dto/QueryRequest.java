package com.ai.documentintelligence.rag.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QueryRequest(
		@Schema(example = "What is the notice period for terminating the contract?")
		@NotBlank @Size(max = 2000) String question,

		@Schema(description = "Number of chunks to retrieve (default app.rag.default-top-k, capped at app.rag.max-top-k)")
		@Min(1) Integer topK,

		@Schema(description = "Minimum cosine similarity 0..1 for a chunk to be used (default app.rag.min-score)")
		@DecimalMin("0.0") @DecimalMax("1.0") Double minScore,

		@Schema(description = "Restrict retrieval to these documents; omit to search all of your documents")
		@Size(max = 100) List<UUID> documentIds) {
}
