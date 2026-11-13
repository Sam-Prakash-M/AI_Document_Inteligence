package com.ai.documentintelligence.rag.dto;

import java.util.UUID;

/**
 * A retrieved chunk shown to the model as source {@code [index]}.
 *
 * @param index   the number the answer uses to cite this source, e.g. {@code [2]}
 * @param cited   whether the answer actually cites this source
 * @param score   cosine similarity to the question (0..1, higher is closer)
 * @param excerpt the chunk text (truncated)
 */
public record SourceCitation(
		int index,
		UUID documentId,
		String filename,
		int chunkIndex,
		double score,
		boolean cited,
		String excerpt) {
}
