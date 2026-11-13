package com.ai.documentintelligence.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ingest raw text without a file upload. */
public record TextDocumentRequest(
		@NotBlank @Size(max = 255) String title,
		@NotBlank @Size(max = 5_000_000) String content) {
}
