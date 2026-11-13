package com.ai.documentintelligence.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ai.documentintelligence.TestProperties;
import com.ai.documentintelligence.config.AppProperties.ApiKey;

class ApiKeyRegistryTest {

	@Test
	void resolvesConfiguredKeyWithDefaultAndOverriddenLimits() {
		var registry = new ApiKeyRegistry(TestProperties.withApiKeys(List.of(
				new ApiKey("acme", "acme-secret-key-0123456789", null),
				new ApiKey("globex", "globex-secret-key-0123456789", 5))));

		assertThat(registry.resolve("acme-secret-key-0123456789")).contains(new ApiClient("acme", 60));
		assertThat(registry.resolve("globex-secret-key-0123456789")).contains(new ApiClient("globex", 5));
		assertThat(registry.resolve("wrong-key-0123456789")).isEmpty();
		assertThat(registry.resolve(null)).isEmpty();
		assertThat(registry.resolve("")).isEmpty();
	}

	@Test
	void ignoresBlankKeys() {
		var registry = new ApiKeyRegistry(TestProperties.withApiKeys(List.of(new ApiKey("default", "", null))));
		assertThat(registry.resolve("")).isEmpty();
	}

	@Test
	void rejectsShortKeys() {
		assertThatThrownBy(() -> new ApiKeyRegistry(
				TestProperties.withApiKeys(List.of(new ApiKey("acme", "short", null)))))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("shorter than");
	}

	@Test
	void rejectsDuplicateKeys() {
		assertThatThrownBy(() -> new ApiKeyRegistry(TestProperties.withApiKeys(List.of(
				new ApiKey("a", "same-secret-key-0123456789", null),
				new ApiKey("b", "same-secret-key-0123456789", null)))))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate");
	}
}
