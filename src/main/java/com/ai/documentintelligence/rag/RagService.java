package com.ai.documentintelligence.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ai.documentintelligence.cache.RagCache;
import com.ai.documentintelligence.common.AiProviderException;
import com.ai.documentintelligence.common.Hashing;
import com.ai.documentintelligence.config.AppProperties;
import com.ai.documentintelligence.rag.dto.QueryRequest;
import com.ai.documentintelligence.rag.dto.QueryResponse;
import com.ai.documentintelligence.rag.dto.SourceCitation;
import com.ai.documentintelligence.vector.ChunkMatch;
import com.ai.documentintelligence.vector.ChunkRepository;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * The question-answering pipeline:
 * <ol>
 * <li>answer cache lookup (Redis) — return immediately on hit</li>
 * <li>embed the question (embedding cache in Redis)</li>
 * <li>top-K cosine search in pgvector, scoped to the caller's READY documents</li>
 * <li>no chunk above {@code minScore} → return the not-found answer <b>without calling the LLM</b></li>
 * <li>build a grounded prompt with numbered sources and call the chat model</li>
 * <li>parse {@code [n]} citations, mark which sources were cited, cache and return</li>
 * </ol>
 */
@Service
public class RagService {

	private static final Logger log = LoggerFactory.getLogger(RagService.class);
	static final int EXCERPT_MAX_CHARS = 500;

	private final EmbeddingModel embeddingModel;
	private final ChatModel chatModel;
	private final ChunkRepository chunks;
	private final RagCache cache;
	private final AppProperties.Rag config;
	private final String chatModelName;

	public RagService(EmbeddingModel embeddingModel, ChatModel chatModel, ChunkRepository chunks, RagCache cache,
			AppProperties props) {
		this.embeddingModel = embeddingModel;
		this.chatModel = chatModel;
		this.chunks = chunks;
		this.cache = cache;
		this.config = props.rag();
		this.chatModelName = props.ai().chat().model();
	}

	public QueryResponse answer(String owner, QueryRequest request) {
		long start = System.nanoTime();
		String question = request.question().strip();
		int topK = Math.min(request.topK() != null ? request.topK() : config.defaultTopK(), config.maxTopK());
		double minScore = request.minScore() != null ? request.minScore() : config.minScore();
		List<UUID> documentIds = request.documentIds() == null ? List.of()
				: request.documentIds().stream().distinct().sorted().toList();

		String fingerprint = fingerprint(question, topK, minScore, documentIds);
		var cached = cache.getAnswer(owner, fingerprint);
		if (cached.isPresent()) {
			return cached.get().withCacheHit(elapsedMs(start));
		}

		float[] queryVector = embedQuestion(question);
		List<ChunkMatch> matches = chunks.search(owner, queryVector, topK, minScore, documentIds);

		QueryResponse response;
		if (matches.isEmpty()) {
			response = new QueryResponse(PromptBuilder.NOT_FOUND_ANSWER, false, List.of(), null, false,
					elapsedMs(start));
		} else {
			String answer = callLlm(question, matches);
			Set<Integer> cited = PromptBuilder.citedIndices(answer, matches.size());
			boolean grounded = !cited.isEmpty() && !answer.contains(PromptBuilder.NOT_FOUND_ANSWER);
			response = new QueryResponse(answer, grounded, toCitations(matches, cited), chatModelName, false,
					elapsedMs(start));
		}
		cache.putAnswer(owner, fingerprint, response);
		log.debug("Answered for '{}' with {} sources in {} ms", owner, matches.size(), response.latencyMs());
		return response;
	}

	private float[] embedQuestion(String question) {
		return cache.getEmbedding(question).orElseGet(() -> {
			float[] v;
			try {
				v = embeddingModel.embed(question).content().vector();
			} catch (RuntimeException e) {
				throw new AiProviderException("Embedding provider failed: " + e.getMessage(), e);
			}
			cache.putEmbedding(question, v);
			return v;
		});
	}

	private String callLlm(String question, List<ChunkMatch> matches) {
		ChatRequest chatRequest = ChatRequest.builder()
				.messages(SystemMessage.from(PromptBuilder.SYSTEM_PROMPT),
						UserMessage.from(PromptBuilder.userMessage(question, matches)))
				.build();
		try {
			ChatResponse response = chatModel.chat(chatRequest);
			String text = response.aiMessage().text();
			if (text == null || text.isBlank()) {
				throw new IllegalStateException("Model returned an empty answer");
			}
			return text.strip();
		} catch (RuntimeException e) {
			throw new AiProviderException("Chat model failed: " + e.getMessage(), e);
		}
	}

	private static List<SourceCitation> toCitations(List<ChunkMatch> matches, Set<Integer> cited) {
		List<SourceCitation> out = new ArrayList<>(matches.size());
		for (int i = 0; i < matches.size(); i++) {
			ChunkMatch m = matches.get(i);
			int index = i + 1;
			out.add(new SourceCitation(index, m.documentId(), m.filename(), m.chunkIndex(),
					Math.round(m.score() * 10_000) / 10_000.0, cited.contains(index), excerpt(m.content())));
		}
		return out;
	}

	static String excerpt(String content) {
		String s = content.strip();
		return s.length() <= EXCERPT_MAX_CHARS ? s : s.substring(0, EXCERPT_MAX_CHARS) + "…";
	}

	static String fingerprint(String question, int topK, double minScore, List<UUID> documentIds) {
		String normalized = question.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
		return Hashing.sha256Hex(normalized + "|" + topK + "|" + minScore + "|" + documentIds);
	}

	private static long elapsedMs(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}
}
