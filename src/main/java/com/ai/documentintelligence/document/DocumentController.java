package com.ai.documentintelligence.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ai.documentintelligence.document.dto.DocumentResponse;
import com.ai.documentintelligence.document.dto.PageResponse;
import com.ai.documentintelligence.document.dto.TextDocumentRequest;
import com.ai.documentintelligence.security.ApiClient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/documents")
@Validated
@Tag(name = "Documents", description = "Upload, list and delete the documents answers are grounded in")
public class DocumentController {

	private final DocumentService service;

	public DocumentController(DocumentService service) {
		this.service = service;
	}

	@Operation(summary = "Upload a file (PDF, DOCX, TXT, MD, HTML, ...) for asynchronous ingestion",
			description = "Returns 202 with status PENDING; poll GET /{id} until READY. "
					+ "Re-uploading identical content returns 200 with the existing document.")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<DocumentResponse> upload(@AuthenticationPrincipal ApiClient client,
			@RequestPart("file") MultipartFile file) throws IOException {
		var result = service.ingest(client.name(), file.getOriginalFilename(), file.getContentType(), file.getBytes());
		return respond(result);
	}

	@Operation(summary = "Ingest raw text as a document")
	@PostMapping(path = "/text", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<DocumentResponse> uploadText(@AuthenticationPrincipal ApiClient client,
			@Valid @RequestBody TextDocumentRequest request) {
		var result = service.ingest(client.name(), request.title(), MediaType.TEXT_PLAIN_VALUE,
				request.content().getBytes(StandardCharsets.UTF_8));
		return respond(result);
	}

	@Operation(summary = "List your documents, newest first")
	@GetMapping
	public PageResponse<DocumentResponse> list(@AuthenticationPrincipal ApiClient client,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		return PageResponse.from(service.list(client.name(), pageable), DocumentResponse::from);
	}

	@Operation(summary = "Get one document (use to poll ingestion status)")
	@GetMapping("/{id}")
	public DocumentResponse get(@AuthenticationPrincipal ApiClient client, @PathVariable UUID id) {
		return DocumentResponse.from(service.get(client.name(), id));
	}

	@Operation(summary = "Delete a document and all its chunks")
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal ApiClient client, @PathVariable UUID id) {
		service.delete(client.name(), id);
	}

	private static ResponseEntity<DocumentResponse> respond(DocumentService.IngestResult result) {
		return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
				.body(DocumentResponse.from(result.document()));
	}
}
