package com.ai.documentintelligence.document.dto;

import java.time.Instant;
import java.util.UUID;

import com.ai.documentintelligence.document.DocumentStatus;
import com.ai.documentintelligence.document.SourceDocument;

public record DocumentResponse(
		UUID id,
		String filename,
		String contentType,
		long sizeBytes,
		DocumentStatus status,
		int chunkCount,
		String errorMessage,
		Instant createdAt,
		Instant updatedAt) {

	public static DocumentResponse from(SourceDocument d) {
		return new DocumentResponse(d.getId(), d.getFilename(), d.getContentType(), d.getSizeBytes(),
				d.getStatus(), d.getChunkCount(), d.getErrorMessage(), d.getCreatedAt(), d.getUpdatedAt());
	}
}
