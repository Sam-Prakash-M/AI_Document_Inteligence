package com.ai.documentintelligence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import com.ai.documentintelligence.security.ApiKeyAuthenticationFilter;
import com.ai.documentintelligence.security.ApiKeyRegistry;
import com.ai.documentintelligence.security.ProblemResponseWriter;
import com.ai.documentintelligence.security.RateLimitFilter;
import com.ai.documentintelligence.security.RateLimiter;

/**
 * Stateless API-key security.
 * <p>
 * Public: health/info probes and the OpenAPI docs. Everything under {@code /api/**}
 * requires a valid API key and is rate limited per client.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AppProperties props, ApiKeyRegistry registry,
			RateLimiter rateLimiter, ProblemResponseWriter problemWriter) throws Exception {

		var apiKeyFilter = new ApiKeyAuthenticationFilter(registry, props.security().apiKeyHeader(), problemWriter);
		var rateLimitFilter = new RateLimitFilter(rateLimiter, problemWriter);

		http
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.requestMatchers("/error").permitAll()
				.requestMatchers("/api/**").hasRole("API_CLIENT")
				.anyRequest().denyAll())
			.exceptionHandling(e -> e
				.authenticationEntryPoint((req, res, ex) ->
						problemWriter.write(req, res, HttpStatus.UNAUTHORIZED,
								"Missing API key: send it in the '" + props.security().apiKeyHeader() + "' header"))
				.accessDeniedHandler((req, res, ex) ->
						problemWriter.write(req, res, HttpStatus.FORBIDDEN, "Access denied")))
			.addFilterBefore(apiKeyFilter, AnonymousAuthenticationFilter.class);

		if (props.security().rateLimit().enabled()) {
			http.addFilterAfter(rateLimitFilter, ApiKeyAuthenticationFilter.class);
		}
		return http.build();
	}
}
