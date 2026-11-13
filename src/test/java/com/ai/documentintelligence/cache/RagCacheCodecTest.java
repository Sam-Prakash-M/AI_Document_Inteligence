package com.ai.documentintelligence.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RagCacheCodecTest {

	@Test
	void embeddingRoundTripsExactly() {
		float[] v = { 0.1f, -0.25f, 3.4028235e38f, 1.4e-45f };
		assertThat(RagCache.decode(RagCache.encode(v))).containsExactly(v);
	}
}
