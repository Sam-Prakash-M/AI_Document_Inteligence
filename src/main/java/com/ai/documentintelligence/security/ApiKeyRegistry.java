package com.ai.documentintelligence.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ai.documentintelligence.common.Hashing;
import com.ai.documentintelligence.config.AppProperties;

/**
 * Holds the configured API keys. Only SHA-256 digests of the keys are kept in memory;
 * lookups hash the presented key and do a map lookup, so the raw secret is never
 * compared character by character.
 */
@Component
public class ApiKeyRegistry {

	private static final Logger log = LoggerFactory.getLogger(ApiKeyRegistry.class);
	static final int MIN_KEY_LENGTH = 16;

	private final Map<String, ApiClient> clientsByKeyHash = new HashMap<>();

	public ApiKeyRegistry(AppProperties props) {
		int defaultLimit = props.security().rateLimit().requestsPerMinute();
		for (AppProperties.ApiKey k : props.security().apiKeys()) {
			if (!StringUtils.hasText(k.key())) {
				continue; // unset env var -> key disabled
			}
			if (!StringUtils.hasText(k.name())) {
				throw new IllegalStateException("Every app.security.api-keys entry needs a name");
			}
			if (k.key().length() < MIN_KEY_LENGTH) {
				throw new IllegalStateException(
						"API key for '" + k.name() + "' is shorter than " + MIN_KEY_LENGTH + " characters");
			}
			int limit = k.requestsPerMinute() != null ? k.requestsPerMinute() : defaultLimit;
			if (clientsByKeyHash.putIfAbsent(Hashing.sha256Hex(k.key()), new ApiClient(k.name(), limit)) != null) {
				throw new IllegalStateException("Duplicate API key configured (client '" + k.name() + "')");
			}
		}
		if (clientsByKeyHash.isEmpty()) {
			log.warn("No API keys configured (app.security.api-keys / APP_API_KEY). Every /api request will be rejected.");
		} else {
			log.info("Loaded {} API key(s)", clientsByKeyHash.size());
		}
	}

	public Optional<ApiClient> resolve(String presentedKey) {
		if (!StringUtils.hasText(presentedKey)) {
			return Optional.empty();
		}
		return Optional.ofNullable(clientsByKeyHash.get(Hashing.sha256Hex(presentedKey)));
	}
}
