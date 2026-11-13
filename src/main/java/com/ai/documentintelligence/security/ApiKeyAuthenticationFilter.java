package com.ai.documentintelligence.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates requests carrying an API key header.
 * <ul>
 * <li>No header: passes through unauthenticated; Spring Security returns 401 for
 * protected paths.</li>
 * <li>Unknown key: rejected immediately with 401.</li>
 * <li>Valid key: an {@link ApiClient} principal is placed in the security context.</li>
 * </ul>
 * Not a Spring bean on purpose, so it is only registered inside the security chain.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

	private final ApiKeyRegistry registry;
	private final String headerName;
	private final ProblemResponseWriter problemWriter;

	public ApiKeyAuthenticationFilter(ApiKeyRegistry registry, String headerName, ProblemResponseWriter problemWriter) {
		this.registry = registry;
		this.headerName = headerName;
		this.problemWriter = problemWriter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String presented = request.getHeader(headerName);
		if (presented == null || presented.isBlank()) {
			chain.doFilter(request, response);
			return;
		}
		var client = registry.resolve(presented.trim());
		if (client.isEmpty()) {
			problemWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Invalid API key");
			return;
		}
		var auth = new UsernamePasswordAuthenticationToken(client.get(), null,
				List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT")));
		SecurityContextHolder.getContext().setAuthentication(auth);
		chain.doFilter(request, response);
	}
}
