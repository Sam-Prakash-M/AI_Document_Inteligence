package com.ai.documentintelligence.vector;

import java.util.UUID;

/**
 * A chunk returned by similarity search.
 *
 * @param score cosine similarity in [-1, 1]; higher is more similar
 */
public record ChunkMatch(UUID documentId, String filename, int chunkIndex, String content, double score) {
}
