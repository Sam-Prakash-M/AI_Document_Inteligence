package com.ai.documentintelligence.cache;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ai.documentintelligence.common.Hashing;
import com.ai.documentintelligence.config.AppProperties;
import com.ai.documentintelligence.rag.dto.QueryResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * Redis caching for the query path.
 * <ul>
 * <li><b>Question embeddings</b> ({@code emb:<model>:<sha256(text)>}): saves an
 * embedding API call for repeated questions. Not tenant-specific: same text, same vector.</li>
 * <li><b>Answers</b> ({@code ans:<owner>:<corpusVersion>:<sha256(request)>}): skips
 * retrieval and the LLM call entirely.</li>
 * </ul>
 * <b>Invalidation</b>: each owner has a corpus version counter ({@code corpus:<owner>}).
 * Ingesting or deleting a document increments it, so every previously cached answer for
 * that owner becomes unreachable at once and simply expires via its TTL. No key scans.
 * <p>
 * Every Redis failure is logged and treated as a cache miss: Redis is an optimisation,
 * never a hard dependency of the query path.
 */
@Component
public class RagCache {

	private static final Logger log = LoggerFactory.getLogger(RagCache.class);

	private final StringRedisTemplate redis;
	private final JsonMapper json;
	private final AppProperties.Cache config;
	private final String embeddingModelName;

	public RagCache(StringRedisTemplate redis, JsonMapper json, AppProperties props) {
		this.redis = redis;
		this.json = json;
		this.config = props.cache();
		this.embeddingModelName = props.ai().embedding().model();
	}

	// ------------------------------------------------------------ embeddings

	public Optional<float[]> getEmbedding(String text) {
		if (!config.enabled()) {
			return Optional.empty();
		}
		try {
			String v = redis.opsForValue().get(embeddingKey(text));
			return v == null ? Optional.empty() : Optional.of(decode(v));
		} catch (RuntimeException e) {
			log.warn("Embedding cache read failed: {}", e.getMessage());
			return Optional.empty();
		}
	}

	public void putEmbedding(String text, float[] vector) {
		if (!config.enabled()) {
			return;
		}
		try {
			redis.opsForValue().set(embeddingKey(text), encode(vector), config.embeddingTtl());
		} catch (RuntimeException e) {
			log.warn("Embedding cache write failed: {}", e.getMessage());
		}
	}

	// --------------------------------------------------------------- answers

	public Optional<QueryResponse> getAnswer(String owner, String requestFingerprint) {
		if (!config.enabled()) {
			return Optional.empty();
		}
		try {
			String v = redis.opsForValue().get(answerKey(owner, requestFingerprint));
			return v == null ? Optional.empty() : Optional.of(json.readValue(v, QueryResponse.class));
		} catch (RuntimeException e) {
			log.warn("Answer cache read failed: {}", e.getMessage());
			return Optional.empty();
		}
	}

	public void putAnswer(String owner, String requestFingerprint, QueryResponse response) {
		if (!config.enabled()) {
			return;
		}
		try {
			redis.opsForValue().set(answerKey(owner, requestFingerprint), json.writeValueAsString(response),
					config.answerTtl());
		} catch (RuntimeException e) {
			log.warn("Answer cache write failed: {}", e.getMessage());
		}
	}

	/** Makes every cached answer of {@code owner} stale. Call after any corpus change. */
	public void invalidateOwner(String owner) {
		try {
			redis.opsForValue().increment(corpusKey(owner));
		} catch (RuntimeException e) {
			log.warn("Cache invalidation for '{}' failed; stale answers may be served until TTL ({}): {}",
					owner, config.answerTtl(), e.getMessage());
		}
	}

	// --------------------------------------------------------------- helpers

	private String answerKey(String owner, String fingerprint) {
		String version = redis.opsForValue().get(corpusKey(owner));
		return "ans:" + owner + ":" + (version == null ? "0" : version) + ":" + fingerprint;
	}

	private static String corpusKey(String owner) {
		return "corpus:" + owner;
	}

	private String embeddingKey(String text) {
		return "emb:" + embeddingModelName + ":" + Hashing.sha256Hex(text);
	}

	static String encode(float[] vector) {
		ByteBuffer buf = ByteBuffer.allocate(vector.length * Float.BYTES);
		for (float f : vector) {
			buf.putFloat(f);
		}
		return Base64.getEncoder().encodeToString(buf.array());
	}

	static float[] decode(String encoded) {
		ByteBuffer buf = ByteBuffer.wrap(Base64.getDecoder().decode(encoded));
		float[] out = new float[buf.remaining() / Float.BYTES];
		for (int i = 0; i < out.length; i++) {
			out[i] = buf.getFloat();
		}
		return out;
	}
}
