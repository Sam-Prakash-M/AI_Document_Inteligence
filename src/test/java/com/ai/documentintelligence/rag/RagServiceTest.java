package com.ai.documentintelligence.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ai.documentintelligence.TestProperties;
import com.ai.documentintelligence.cache.RagCache;
import com.ai.documentintelligence.common.AiProviderException;
import com.ai.documentintelligence.rag.dto.QueryRequest;
import com.ai.documentintelligence.rag.dto.QueryResponse;
import com.ai.documentintelligence.vector.ChunkMatch;
import com.ai.documentintelligence.vector.ChunkRepository;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

class RagServiceTest {

	private EmbeddingModel embeddingModel;
	private ChatModel chatModel;
	private ChunkRepository chunks;
	private RagCache cache;
	private RagService service;

	@BeforeEach
	void setUp() {
		embeddingModel = mock(EmbeddingModel.class);
		chatModel = mock(ChatModel.class);
		chunks = mock(ChunkRepository.class);
		cache = mock(RagCache.class);
		when(cache.getAnswer(anyString(), anyString())).thenReturn(Optional.empty());
		when(cache.getEmbedding(anyString())).thenReturn(Optional.empty());
		when(embeddingModel.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[] { 1f, 0f, 0f })));
		service = new RagService(embeddingModel, chatModel, chunks, cache, TestProperties.defaults());
	}

	@Test
	void answersWithCitationsAndMarksCitedSources() {
		var docId = UUID.randomUUID();
		when(chunks.search(eq("acme"), any(), anyInt(), anyDouble(), any())).thenReturn(List.of(
				new ChunkMatch(docId, "contract.pdf", 4, "Notice period is 30 days.", 0.87654),
				new ChunkMatch(docId, "contract.pdf", 9, "Unrelated clause.", 0.51)));
		when(chatModel.chat(any(ChatRequest.class))).thenReturn(
				ChatResponse.builder().aiMessage(AiMessage.from("The notice period is 30 days [1].")).build());

		QueryResponse r = service.answer("acme", new QueryRequest("What is the notice period?", null, null, null));

		assertThat(r.grounded()).isTrue();
		assertThat(r.cached()).isFalse();
		assertThat(r.answer()).contains("[1]");
		assertThat(r.sources()).hasSize(2);
		assertThat(r.sources().get(0).cited()).isTrue();
		assertThat(r.sources().get(0).score()).isEqualTo(0.8765);
		assertThat(r.sources().get(1).cited()).isFalse();
		verify(cache).putEmbedding(eq("What is the notice period?"), any());
		verify(cache).putAnswer(eq("acme"), anyString(), eq(r));
	}

	@Test
	void doesNotCallLlmWhenNothingRelevantIsFound() {
		when(chunks.search(anyString(), any(), anyInt(), anyDouble(), any())).thenReturn(List.of());

		QueryResponse r = service.answer("acme", new QueryRequest("Anything?", null, null, null));

		assertThat(r.grounded()).isFalse();
		assertThat(r.answer()).isEqualTo(PromptBuilder.NOT_FOUND_ANSWER);
		assertThat(r.sources()).isEmpty();
		verify(chatModel, never()).chat(any(ChatRequest.class));
	}

	@Test
	void notGroundedWhenModelDeclines() {
		when(chunks.search(anyString(), any(), anyInt(), anyDouble(), any())).thenReturn(
				List.of(new ChunkMatch(UUID.randomUUID(), "a.txt", 0, "text", 0.6)));
		when(chatModel.chat(any(ChatRequest.class))).thenReturn(
				ChatResponse.builder().aiMessage(AiMessage.from(PromptBuilder.NOT_FOUND_ANSWER)).build());

		assertThat(service.answer("acme", new QueryRequest("q", null, null, null)).grounded()).isFalse();
	}

	@Test
	void returnsCachedAnswerWithoutRetrievalOrLlm() {
		var cachedResponse = new QueryResponse("cached [1]", true, List.of(), "m", false, 900);
		when(cache.getAnswer(eq("acme"), anyString())).thenReturn(Optional.of(cachedResponse));

		QueryResponse r = service.answer("acme", new QueryRequest("q", null, null, null));

		assertThat(r.cached()).isTrue();
		assertThat(r.answer()).isEqualTo("cached [1]");
		verify(embeddingModel, never()).embed(anyString());
		verify(chatModel, never()).chat(any(ChatRequest.class));
	}

	@Test
	void capsTopKAtConfiguredMaximum() {
		when(chunks.search(anyString(), any(), anyInt(), anyDouble(), any())).thenReturn(List.of());

		service.answer("acme", new QueryRequest("q", 500, null, null));

		verify(chunks).search(eq("acme"), any(), eq(20), anyDouble(), any());
	}

	@Test
	void wrapsProviderFailures() {
		when(chunks.search(anyString(), any(), anyInt(), anyDouble(), any())).thenReturn(
				List.of(new ChunkMatch(UUID.randomUUID(), "a.txt", 0, "text", 0.6)));
		when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("quota exceeded"));

		assertThatThrownBy(() -> service.answer("acme", new QueryRequest("q", null, null, null)))
				.isInstanceOf(AiProviderException.class).hasMessageContaining("quota exceeded");
	}

	@Test
	void fingerprintIgnoresCaseWhitespaceAndDocumentOrder() {
		var a = UUID.randomUUID();
		var b = UUID.randomUUID();
		var sorted = List.of(a, b).stream().sorted().toList();
		assertThat(RagService.fingerprint("What  is X?", 5, 0.3, sorted))
				.isEqualTo(RagService.fingerprint("what is x?", 5, 0.3, sorted))
				.isNotEqualTo(RagService.fingerprint("what is x?", 6, 0.3, sorted));
	}
}
