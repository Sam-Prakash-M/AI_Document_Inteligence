package com.ai.documentintelligence.common;

/** The upstream LLM / embedding provider failed (timeout, quota, outage). Mapped to 502. */
public class AiProviderException extends RuntimeException {

	public AiProviderException(String message, Throwable cause) {
		super(message, cause);
	}
}
