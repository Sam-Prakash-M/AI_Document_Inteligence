package com.ai.documentintelligence.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RateLimiterTest {

	private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
	private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1_000_040), ZoneOffset.UTC); // 40s left in window

	@SuppressWarnings("unchecked")
	private void counterReturns(Long value) {
		when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(value);
	}

	@Test
	void allowsUpToTheLimit() {
		counterReturns(3L);
		var d = new RateLimiter(redis, clock).tryAcquire("acme", 3);
		assertThat(d.allowed()).isTrue();
		assertThat(d.remaining()).isZero();
		assertThat(d.resetInSeconds()).isEqualTo(40);
	}

	@Test
	void rejectsAboveTheLimit() {
		counterReturns(4L);
		assertThat(new RateLimiter(redis, clock).tryAcquire("acme", 3).allowed()).isFalse();
	}

	@Test
	@SuppressWarnings("unchecked")
	void failsOpenWhenRedisIsDown() {
		when(redis.execute(any(RedisScript.class), anyList(), anyString()))
				.thenThrow(new RuntimeException("connection refused"));
		assertThat(new RateLimiter(redis, clock).tryAcquire("acme", 3).allowed()).isTrue();
	}
}
