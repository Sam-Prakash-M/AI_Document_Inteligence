package com.ai.documentintelligence.vector;

/** A chunk ready to be stored: its position in the document, text and embedding. */
public record NewChunk(int chunkIndex, String content, float[] embedding) {
}
