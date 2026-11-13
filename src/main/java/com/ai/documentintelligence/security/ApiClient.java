package com.ai.documentintelligence.security;

/**
 * An authenticated API caller. {@code name} doubles as the tenant id: every document
 * and every query is scoped to it.
 *
 * @param name              tenant / client name from configuration
 * @param requestsPerMinute effective rate limit for this client
 */
public record ApiClient(String name, int requestsPerMinute) {
}
