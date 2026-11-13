package com.ai.documentintelligence.rag.dto;

import java.util.List;

/**
 * @param answer    the model's answer, with inline citations like {@code [1]}
 * @param grounded  true when the answer cites at least one retrieved source; false when
 *                  nothing relevant was found or the model declined to answer
 * @param sources   every chunk that was given to the model, in citation order
 * @param model     chat model that produced the answer (null if no LLM call was made)
 * @param cached    true when served from the Redis answer cache
 * @param latencyMs server-side processing time for this request
 */
public record QueryResponse(
		String answer,
		boolean grounded,
		List<SourceCitation> sources,
		String model,
		boolean cached,
		long latencyMs) {

	public QueryResponse withCacheHit(long latencyMs) {
		return new QueryResponse(answer, grounded, sources, model, true, latencyMs);
	}
}
