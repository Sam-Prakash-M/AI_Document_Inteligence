package com.ai.documentintelligence.document;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import com.ai.documentintelligence.TestProperties;
import com.ai.documentintelligence.cache.RagCache;
import com.ai.documentintelligence.vector.ChunkRepository;

import dev.langchain4j.model.embedding.EmbeddingModel;

class IngestionWorkerTest {

	private final SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
	private final ChunkRepository chunks = mock(ChunkRepository.class);
	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	private final IngestionWorker worker = new IngestionWorker(documents, chunks, embeddingModel,
			mock(RagCache.class), mock(TransactionTemplate.class), TestProperties.defaults());

	@Test
	void marksFailedEvenWhenChunkCleanupAlsoFails() {
		UUID id = UUID.randomUUID();
		when(documents.findById(id)).thenReturn(Optional.of(mock(SourceDocument.class)));
		when(documents.markProcessing(id)).thenReturn(1);
		when(embeddingModel.embedAll(anyList())).thenThrow(new IllegalStateException("provider quota exhausted"));
		doThrow(new DataAccessResourceFailureException("chunk table unavailable")).when(chunks).deleteByDocument(id);

		worker.process(id, "Some plain text to ingest.".getBytes(StandardCharsets.UTF_8));

		// Otherwise the document would sit in PROCESSING until the next restart.
		verify(documents).markFailed(eq(id), contains("provider quota exhausted"));
	}
}
