package com.ai.documentintelligence.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Metadata for an uploaded document. Chunk text and embeddings live in
 * {@code document_chunks} and are accessed through {@code ChunkRepository} (JDBC),
 * because JPA has no native mapping for the pgvector type.
 */
@Entity
@Table(name = "documents")
public class SourceDocument {

	@Id
	private UUID id;

	@Column(nullable = false, length = 100)
	private String owner;

	@Column(nullable = false, length = 512)
	private String filename;

	@Column(name = "content_type", length = 255)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(nullable = false, length = 64)
	private String sha256;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DocumentStatus status;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "chunk_count", nullable = false)
	private int chunkCount;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected SourceDocument() {
	}

	public SourceDocument(String owner, String filename, String contentType, long sizeBytes, String sha256) {
		this.id = UUID.randomUUID();
		this.owner = owner;
		this.filename = filename;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.sha256 = sha256;
		this.status = DocumentStatus.PENDING;
	}

	@PrePersist
	void onCreate() {
		createdAt = updatedAt = Instant.now();
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public void markProcessing() {
		status = DocumentStatus.PROCESSING;
		errorMessage = null;
	}

	public void markReady(int chunks) {
		status = DocumentStatus.READY;
		chunkCount = chunks;
		errorMessage = null;
	}

	public void markFailed(String reason) {
		status = DocumentStatus.FAILED;
		chunkCount = 0;
		errorMessage = reason == null ? "Unknown error"
				: reason.length() > 1000 ? reason.substring(0, 1000) : reason;
	}

	public UUID getId() { return id; }
	public String getOwner() { return owner; }
	public String getFilename() { return filename; }
	public String getContentType() { return contentType; }
	public long getSizeBytes() { return sizeBytes; }
	public String getSha256() { return sha256; }
	public DocumentStatus getStatus() { return status; }
	public String getErrorMessage() { return errorMessage; }
	public int getChunkCount() { return chunkCount; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
