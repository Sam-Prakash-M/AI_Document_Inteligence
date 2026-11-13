package com.ai.documentintelligence.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

/**
 * Builds the LangChain4j chat and embedding models from {@code app.ai.*}.
 * <p>
 * The provider is chosen at startup; the rest of the application only depends on
 * the provider-neutral {@link ChatModel} and {@link EmbeddingModel} interfaces.
 */
@Configuration
public class AiModelConfig {

	private static final Logger log = LoggerFactory.getLogger(AiModelConfig.class);

	@Bean
	ChatModel chatModel(AppProperties props) {
		AppProperties.Chat c = props.ai().chat();
		log.info("Chat model: provider={} model={}", c.provider(), c.model());
		return switch (c.provider()) {
			case OPENAI -> {
				var b = OpenAiChatModel.builder()
						.apiKey(required(c.apiKey(), "app.ai.chat.api-key (OPENAI_API_KEY)"))
						.modelName(c.model())
						.temperature(c.temperature())
						.maxTokens(c.maxTokens())
						.timeout(c.timeout());
				if (StringUtils.hasText(c.baseUrl())) {
					b.baseUrl(c.baseUrl());
				}
				yield b.build();
			}
			case ANTHROPIC -> {
				var b = AnthropicChatModel.builder()
						.apiKey(required(c.apiKey(), "app.ai.chat.api-key (ANTHROPIC_API_KEY)"))
						.modelName(c.model())
						.temperature(c.temperature())
						.maxTokens(c.maxTokens())
						.timeout(c.timeout());
				if (StringUtils.hasText(c.baseUrl())) {
					b.baseUrl(c.baseUrl());
				}
				yield b.build();
			}
			case OLLAMA -> OllamaChatModel.builder()
					.baseUrl(required(c.baseUrl(), "app.ai.chat.base-url (OLLAMA_BASE_URL)"))
					.modelName(c.model())
					.temperature(c.temperature())
					.numPredict(c.maxTokens())
					.timeout(c.timeout())
					.build();
		};
	}

	@Bean
	EmbeddingModel embeddingModel(AppProperties props) {
		AppProperties.Embedding e = props.ai().embedding();
		log.info("Embedding model: provider={} model={} dimension={}", e.provider(), e.model(), e.dimension());
		return switch (e.provider()) {
			case OPENAI -> {
				var b = OpenAiEmbeddingModel.builder()
						.apiKey(required(e.apiKey(), "app.ai.embedding.api-key (OPENAI_API_KEY)"))
						.modelName(e.model())
						.timeout(e.timeout());
				if (StringUtils.hasText(e.baseUrl())) {
					b.baseUrl(e.baseUrl());
				}
				yield b.build();
			}
			case OLLAMA -> OllamaEmbeddingModel.builder()
					.baseUrl(required(e.baseUrl(), "app.ai.embedding.base-url (OLLAMA_BASE_URL)"))
					.modelName(e.model())
					.timeout(e.timeout())
					.build();
		};
	}

	private static String required(String value, String name) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalStateException("Missing required configuration: " + name);
		}
		return value;
	}
}
