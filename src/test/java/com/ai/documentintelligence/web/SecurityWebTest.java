package com.ai.documentintelligence.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ai.documentintelligence.common.GlobalExceptionHandler;
import com.ai.documentintelligence.config.AppProperties;
import com.ai.documentintelligence.config.SecurityConfig;
import com.ai.documentintelligence.rag.QueryController;
import com.ai.documentintelligence.rag.RagService;
import com.ai.documentintelligence.rag.dto.QueryResponse;
import com.ai.documentintelligence.security.ApiKeyRegistry;
import com.ai.documentintelligence.security.ProblemResponseWriter;
import com.ai.documentintelligence.security.RateLimiter;

/** Web + security slice: real filter chain, mocked RAG service and Redis rate limiter. */
@WebMvcTest(QueryController.class)
@Import({ SecurityConfig.class, ApiKeyRegistry.class, ProblemResponseWriter.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = {
		"app.security.api-keys[0].name=acme",
		"app.security.api-keys[0].key=acme-secret-key-0123456789",
		"app.ai.chat.provider=openai", "app.ai.chat.model=test-model",
		"app.ai.embedding.provider=openai", "app.ai.embedding.model=test-embed", "app.ai.embedding.dimension=3",
		"spring.autoconfigure.exclude=org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration" })
class SecurityWebTest {

	private static final String BODY = "{\"question\":\"What is the notice period?\"}";

	@Autowired
	MockMvc mvc;

	@MockitoBean
	RagService ragService;

	@MockitoBean
	RateLimiter rateLimiter;

	@Test
	void rejectsMissingKey() throws Exception {
		mvc.perform(post("/api/v1/query").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Missing API key: send it in the 'X-API-Key' header"));
	}

	@Test
	void rejectsWrongKey() throws Exception {
		mvc.perform(post("/api/v1/query").header("X-API-Key", "nope-nope-nope-nope")
						.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Invalid API key"));
	}

	@Test
	void answersWithValidKeyAndSetsRateLimitHeaders() throws Exception {
		when(rateLimiter.tryAcquire("acme", 60)).thenReturn(new RateLimiter.Decision(true, 60, 59, 30));
		when(ragService.answer(eq("acme"), any()))
				.thenReturn(new QueryResponse("30 days [1].", true, List.of(), "test-model", false, 12));

		mvc.perform(post("/api/v1/query").header("X-API-Key", "acme-secret-key-0123456789")
						.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isOk())
				.andExpect(header().string("X-RateLimit-Remaining", "59"))
				.andExpect(jsonPath("$.answer").value("30 days [1]."))
				.andExpect(jsonPath("$.grounded").value(true));
	}

	@Test
	void returns429WhenRateLimited() throws Exception {
		when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(new RateLimiter.Decision(false, 60, 0, 17));

		mvc.perform(post("/api/v1/query").header("X-API-Key", "acme-secret-key-0123456789")
						.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "17"));
	}

	@Test
	void validatesRequestBody() throws Exception {
		when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(new RateLimiter.Decision(true, 60, 59, 30));

		mvc.perform(post("/api/v1/query").header("X-API-Key", "acme-secret-key-0123456789")
						.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unexpectedErrorsAreGenericProblemResponses() throws Exception {
		when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(new RateLimiter.Decision(true, 60, 59, 30));
		when(ragService.answer(eq("acme"), any()))
				.thenThrow(new IllegalStateException("could not load library C:/secret/path/vector.dll"));

		mvc.perform(post("/api/v1/query").header("X-API-Key", "acme-secret-key-0123456789")
						.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value("Unexpected server error"))
				.andExpect(jsonPath("$.trace").doesNotExist());
	}
}
