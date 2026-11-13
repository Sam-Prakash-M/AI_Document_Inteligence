package com.ai.documentintelligence;

import java.time.Duration;
import java.util.List;

import com.ai.documentintelligence.config.AppProperties;

/** Builds an {@link AppProperties} matching application.yml defaults, for plain unit tests. */
public final class TestProperties {

	private TestProperties() {
	}

	public static AppProperties defaults() {
		return withApiKeys(List.of(new AppProperties.ApiKey("acme", "acme-secret-key-0123456789", null)));
	}

	public static AppProperties withApiKeys(List<AppProperties.ApiKey> keys) {
		return new AppProperties(
				new AppProperties.Security("X-API-Key", keys, new AppProperties.RateLimit(true, 60)),
				new AppProperties.Ai(
						new AppProperties.Chat(AppProperties.ChatProvider.OPENAI, "test-chat-model", "k", null, 0.1,
								1024, Duration.ofSeconds(60)),
						new AppProperties.Embedding(AppProperties.EmbeddingProvider.OPENAI, "test-embed-model", "k",
								null, 3, Duration.ofSeconds(60))),
				new AppProperties.Ingestion(1000, 150, 64, 2, 100),
				new AppProperties.Rag(5, 20, 0.3),
				new AppProperties.Cache(true, Duration.ofHours(1), Duration.ofHours(24)));
	}
}
