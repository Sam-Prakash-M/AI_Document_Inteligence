package com.ai.documentintelligence.document;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.ai.documentintelligence.cache.RagCache;
import com.ai.documentintelligence.config.AppProperties;
import com.ai.documentintelligence.config.AsyncConfig;
import com.ai.documentintelligence.vector.ChunkRepository;
import com.ai.documentintelligence.vector.NewChunk;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * The ingestion pipeline: <b>parse</b> (Apache Tika: PDF, DOCX, HTML, TXT, MD, ...) →
 * <b>chunk</b> (recursive splitter with overlap) → <b>embed</b> (batched) →
 * <b>store</b> (pgvector) → mark READY and invalidate the owner's answer cache.
 * <p>
 * Runs on the bounded {@link AsyncConfig#INGESTION_EXECUTOR}. Storing chunks and
 * marking READY happen in one transaction, so a document is never READY with partial
 * chunks, and any failure leaves it FAILED with no chunks.
 */
@Component
public class IngestionWorker {

	private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);

	private final SourceDocumentRepository documents;
	private final ChunkRepository chunks;
	private final EmbeddingModel embeddingModel;
	private final RagCache cache;
	private final TransactionTemplate tx;
	private final AppProperties.Ingestion config;
	private final DocumentSplitter splitter;

	public IngestionWorker(SourceDocumentRepository documents, ChunkRepository chunks, EmbeddingModel embeddingModel,
			RagCache cache, TransactionTemplate tx, AppProperties props) {
		this.documents = documents;
		this.chunks = chunks;
		this.embeddingModel = embeddingModel;
		this.cache = cache;
		this.tx = tx;
		this.config = props.ingestion();
		this.splitter = DocumentSplitters.recursive(config.chunkSize(), config.chunkOverlap());
	}

	@Async(AsyncConfig.INGESTION_EXECUTOR)
	public void process(UUID documentId, byte[] bytes) {
		SourceDocument doc = documents.findById(documentId).orElse(null);
		if (doc == null || documents.markProcessing(documentId) == 0) {
			log.info("Document {} was deleted before ingestion started", documentId);
			return;
		}
		long start = System.currentTimeMillis();
		try {
			String text = extractText(bytes);
			List<TextSegment> segments = splitter.split(Document.from(text));
			if (segments.isEmpty()) {
				throw new IllegalStateException("Document produced no text chunks");
			}
			List<NewChunk> toStore = embed(segments);

			Boolean stored = tx.execute(status -> {
				if (documents.markReady(documentId, toStore.size()) == 0) {
					return false; // deleted while we were embedding
				}
				chunks.replaceChunks(documentId, doc.getOwner(), toStore);
				return true;
			});
			if (!Boolean.TRUE.equals(stored)) {
				log.info("Document {} was deleted during ingestion, chunks discarded", documentId);
				return;
			}
			cache.invalidateOwner(doc.getOwner());
			log.info("Ingested document {} ({}) into {} chunks in {} ms", documentId, doc.getFilename(),
					toStore.size(), System.currentTimeMillis() - start);
		} catch (Exception e) {
			log.warn("Ingestion of document {} failed: {}", documentId, e.toString());
			// Mark FAILED first and on its own: if chunk cleanup throws as well (for example
			// the same database fault that broke ingestion), the document must not be left
			// in PROCESSING forever.
			try {
				documents.markFailed(documentId, truncate(describe(e)));
			} catch (RuntimeException inner) {
				log.error("Could not mark document {} as FAILED", documentId, inner);
			}
			try {
				chunks.deleteByDocument(documentId);
			} catch (RuntimeException inner) {
				log.error("Could not remove chunks of failed document {}", documentId, inner);
			}
		}
	}

	private String extractText(byte[] bytes) {
		Document parsed;
		try {
			parsed = new ApacheTikaDocumentParser().parse(new ByteArrayInputStream(bytes));
		} catch (RuntimeException e) {
			throw new IllegalStateException("Could not extract text (unsupported, encrypted or empty file)", e);
		}
		String text = parsed == null ? null : parsed.text();
		if (text == null || text.isBlank()) {
			throw new IllegalStateException("No extractable text (scanned PDFs need OCR first)");
		}
		return text;
	}

	private List<NewChunk> embed(List<TextSegment> segments) {
		List<NewChunk> out = new ArrayList<>(segments.size());
		int batch = config.embedBatchSize();
		for (int from = 0; from < segments.size(); from += batch) {
			List<TextSegment> slice = segments.subList(from, Math.min(from + batch, segments.size()));
			List<Embedding> vectors = embeddingModel.embedAll(slice).content();
			if (vectors.size() != slice.size()) {
				throw new IllegalStateException("Embedding provider returned " + vectors.size()
						+ " vectors for " + slice.size() + " segments");
			}
			for (int i = 0; i < slice.size(); i++) {
				out.add(new NewChunk(from + i, slice.get(i).text(), vectors.get(i).vector()));
			}
		}
		return out;
	}

	private static String describe(Exception e) {
		String msg = e.getMessage();
		return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
	}

	private static String truncate(String s) {
		return s.length() > 1000 ? s.substring(0, 1000) : s;
	}
}
