package com.ai.documentintelligence.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Applies the per-client rate limit to authenticated requests and exposes the
 * standard {@code X-RateLimit-*} headers. Runs after {@link ApiKeyAuthenticationFilter}.
 */
public class RateLimitFilter extends OncePerRequestFilter {

	private final RateLimiter rateLimiter;
	private final ProblemResponseWriter problemWriter;

	public RateLimitFilter(RateLimiter rateLimiter, ProblemResponseWriter problemWriter) {
		this.rateLimiter = rateLimiter;
		this.problemWriter = problemWriter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof ApiClient client)) {
			chain.doFilter(request, response);
			return;
		}
		RateLimiter.Decision d = rateLimiter.tryAcquire(client.name(), client.requestsPerMinute());
		response.setHeader("X-RateLimit-Limit", String.valueOf(d.limit()));
		response.setHeader("X-RateLimit-Remaining", String.valueOf(d.remaining()));
		response.setHeader("X-RateLimit-Reset", String.valueOf(d.resetInSeconds()));
		if (!d.allowed()) {
			response.setHeader("Retry-After", String.valueOf(d.resetInSeconds()));
			problemWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
					"Rate limit of " + d.limit() + " requests per minute exceeded");
			return;
		}
		chain.doFilter(request, response);
	}
}
