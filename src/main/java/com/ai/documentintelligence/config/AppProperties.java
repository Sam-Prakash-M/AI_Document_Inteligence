package com.ai.documentintelligence.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * All application-specific configuration, bound from the {@code app.*} namespace
 * (see {@code application.yml}). Every value can be overridden with an environment
 * variable, e.g. {@code APP_AI_CHAT_PROVIDER=anthropic}.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
		@Valid @NotNull Security security,
		@Valid @NotNull Ai ai,
		@Valid @NotNull Ingestion ingestion,
		@Valid @NotNull Rag rag,
		@Valid @NotNull Cache cache) {

	// ---------------------------------------------------------------- security

	public record Security(
			@DefaultValue("X-API-Key") String apiKeyHeader,
			List<ApiKey> apiKeys,
			@Valid @NotNull RateLimit rateLimit) {

		public List<ApiKey> apiKeys() {
			return apiKeys == null ? List.of() : apiKeys;
		}
	}

	/**
	 * One API client. {@code name} identifies the tenant: documents uploaded with a key
	 * are only visible to keys with the same name. {@code requestsPerMinute} optionally
	 * overrides the global rate limit for this client.
	 */
	public record ApiKey(String name, String key, Integer requestsPerMinute) {
	}

	public record RateLimit(
			@DefaultValue("true") boolean enabled,
			@DefaultValue("60") @Min(1) int requestsPerMinute) {
	}

	// ---------------------------------------------------------------------- ai

	public record Ai(@Valid @NotNull Chat chat, @Valid @NotNull Embedding embedding) {
	}

	public enum ChatProvider { OPENAI, ANTHROPIC, OLLAMA }

	public enum EmbeddingProvider { OPENAI, OLLAMA }

	public record Chat(
			@NotNull ChatProvider provider,
			@NotBlank String model,
			String apiKey,
			String baseUrl,
			@DefaultValue("0.1") double temperature,
			@DefaultValue("1024") int maxTokens,
			@DefaultValue("60s") Duration timeout) {
	}

	public record Embedding(
			@NotNull EmbeddingProvider provider,
			@NotBlank String model,
			String apiKey,
			String baseUrl,
			@Min(1) int dimension,
			@DefaultValue("60s") Duration timeout) {
	}

	// --------------------------------------------------------------- ingestion

	public record Ingestion(
			@DefaultValue("1000") @Min(100) int chunkSize,
			@DefaultValue("150") @Min(0) int chunkOverlap,
			@DefaultValue("64") @Min(1) int embedBatchSize,
			@DefaultValue("2") @Min(1) int concurrency,
			@DefaultValue("100") @Min(1) int queueCapacity) {
	}

	// --------------------------------------------------------------------- rag

	public record Rag(
			@DefaultValue("5") @Min(1) int defaultTopK,
			@DefaultValue("20") @Min(1) int maxTopK,
			@DefaultValue("0.3") @DecimalMin("0.0") @DecimalMax("1.0") double minScore) {
	}

	// ------------------------------------------------------------------- cache

	public record Cache(
			@DefaultValue("true") boolean enabled,
			@DefaultValue("1h") Duration answerTtl,
			@DefaultValue("24h") Duration embeddingTtl) {
	}
}
