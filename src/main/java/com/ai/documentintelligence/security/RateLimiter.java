package com.ai.documentintelligence.security;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Fixed-window rate limiter backed by Redis (one counter per client per minute).
 * <p>
 * The INCR + EXPIRE pair runs as a Lua script so it is atomic across app replicas.
 * If Redis is unavailable the limiter <b>fails open</b>: availability of the API is
 * preferred over strict limiting, and a warning is logged.
 */
@Component
public class RateLimiter {

	private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
	private static final long WINDOW_SECONDS = 60;

	private static final RedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
			local count = redis.call('INCR', KEYS[1])
			if count == 1 then
			  redis.call('EXPIRE', KEYS[1], ARGV[1])
			end
			return count
			""", Long.class);

	private final StringRedisTemplate redis;
	private final Clock clock;

	// Two constructors exist (the package-private one lets tests pin the clock), so
	// Spring needs to be told which one to use or it looks for a no-arg constructor.
	@Autowired
	public RateLimiter(StringRedisTemplate redis) {
		this(redis, Clock.systemUTC());
	}

	RateLimiter(StringRedisTemplate redis, Clock clock) {
		this.redis = redis;
		this.clock = clock;
	}

	public Decision tryAcquire(String clientName, int limit) {
		long nowSeconds = clock.instant().getEpochSecond();
		long window = nowSeconds / WINDOW_SECONDS;
		long resetAt = (window + 1) * WINDOW_SECONDS;
		String key = "rl:" + clientName + ":" + window;
		try {
			Long count = redis.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(WINDOW_SECONDS + 5));
			long used = count == null ? 0 : count;
			return new Decision(used <= limit, limit, Math.max(0, limit - used), resetAt - nowSeconds);
		} catch (RuntimeException e) {
			log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
			return new Decision(true, limit, limit, resetAt - nowSeconds);
		}
	}

	public record Decision(boolean allowed, int limit, long remaining, long resetInSeconds) {
	}
}
