package com.ai.documentintelligence.rag;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ai.documentintelligence.rag.dto.QueryRequest;
import com.ai.documentintelligence.rag.dto.QueryResponse;
import com.ai.documentintelligence.security.ApiClient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/query")
@Tag(name = "Query", description = "Ask questions answered from your documents, with citations")
public class QueryController {

	private final RagService ragService;

	public QueryController(RagService ragService) {
		this.ragService = ragService;
	}

	@Operation(summary = "Ask a question",
			description = "Retrieves the most relevant chunks of your READY documents and returns an answer "
					+ "that cites them as [n], plus the sources themselves.")
	@PostMapping
	public QueryResponse query(@AuthenticationPrincipal ApiClient client, @Valid @RequestBody QueryRequest request) {
		return ragService.answer(client.name(), request);
	}
}
