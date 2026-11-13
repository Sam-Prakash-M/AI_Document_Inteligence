package com.ai.documentintelligence.document;

/** Lifecycle: PENDING -> PROCESSING -> READY | FAILED. Only READY documents are searchable. */
public enum DocumentStatus {
	PENDING, PROCESSING, READY, FAILED
}
