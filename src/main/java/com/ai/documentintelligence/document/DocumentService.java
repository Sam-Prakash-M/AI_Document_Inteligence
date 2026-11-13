package com.ai.documentintelligence.document;

import java.util.EnumSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ai.documentintelligence.cache.RagCache;
import com.ai.documentintelligence.common.Hashing;
import com.ai.documentintelligence.common.NotFoundException;

/**
 * Document CRUD and the entry point for ingestion. Uploads are stored as PENDING and
 * handed to {@link IngestionWorker}, which parses, chunks and embeds them asynchronously.
 * <p>
 * Methods that hand work to the worker are deliberately <b>not</b> transactional:
 * the PENDING row must be committed before the async job looks it up.
 */
@Service
public class DocumentService {

	private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

	private final SourceDocumentRepository documents;
	private final IngestionWorker worker;
	private final RagCache cache;

	public DocumentService(SourceDocumentRepository documents, IngestionWorker worker, RagCache cache) {
		this.documents = documents;
		this.worker = worker;
		this.cache = cache;
	}

	/**
	 * @return the stored document and whether it was newly created ({@code false} when an
	 *         identical file was already uploaded by this client and is not FAILED)
	 */
	public IngestResult ingest(String owner, String filename, String contentType, byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			throw new IllegalArgumentException("File is empty");
		}
		String safeName = sanitizeFilename(filename);
		String sha = Hashing.sha256Hex(bytes);

		var existing = documents.findFirstByOwnerAndSha256AndStatusNot(owner, sha, DocumentStatus.FAILED);
		if (existing.isPresent()) {
			log.info("Duplicate upload by '{}' of {} -> existing document {}", owner, safeName, existing.get().getId());
			return new IngestResult(existing.get(), false);
		}

		SourceDocument doc = documents.save(new SourceDocument(owner, safeName, contentType, bytes.length, sha));
		try {
			worker.process(doc.getId(), bytes);
		} catch (RuntimeException e) { // queue full
			doc.markFailed("Ingestion queue full, please re-upload");
			documents.save(doc);
			throw e;
		}
		return new IngestResult(doc, true);
	}

	@Transactional(readOnly = true)
	public Page<SourceDocument> list(String owner, Pageable pageable) {
		return documents.findByOwner(owner, pageable);
	}

	@Transactional(readOnly = true)
	public SourceDocument get(String owner, UUID id) {
		return documents.findByIdAndOwner(id, owner)
				.orElseThrow(() -> new NotFoundException("Document " + id + " not found"));
	}

	/** Deletes the document; its chunks go with it via ON DELETE CASCADE. */
	@Transactional
	public void delete(String owner, UUID id) {
		documents.delete(get(owner, id));
		cache.invalidateOwner(owner);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void failInterruptedJobs() {
		int n = documents.failAllInStatus(EnumSet.of(DocumentStatus.PENDING, DocumentStatus.PROCESSING),
				"Interrupted by application restart, please re-upload");
		if (n > 0) {
			log.warn("Marked {} interrupted ingestion job(s) as FAILED", n);
		}
	}

	static String sanitizeFilename(String filename) {
		if (!StringUtils.hasText(filename)) {
			return "untitled";
		}
		String name = StringUtils.getFilename(filename.replace('\\', '/'));
		name = name == null ? "untitled" : name.replaceAll("[\\p{Cntrl}]", "").trim();
		if (name.isEmpty()) {
			return "untitled";
		}
		return name.length() > 255 ? name.substring(0, 255) : name;
	}

	public record IngestResult(SourceDocument document, boolean created) {
	}
}
