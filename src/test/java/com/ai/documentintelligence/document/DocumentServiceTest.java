package com.ai.documentintelligence.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentServiceTest {

	@Test
	void sanitizesFilenames() {
		assertThat(DocumentService.sanitizeFilename("C:\\Users\\me\\report.pdf")).isEqualTo("report.pdf");
		assertThat(DocumentService.sanitizeFilename("../../etc/passwd")).isEqualTo("passwd");
		assertThat(DocumentService.sanitizeFilename("bad\u0000name.txt")).isEqualTo("badname.txt");
		assertThat(DocumentService.sanitizeFilename(null)).isEqualTo("untitled");
		assertThat(DocumentService.sanitizeFilename("dir/")).isEqualTo("untitled");
		assertThat(DocumentService.sanitizeFilename("x".repeat(300) + ".txt")).hasSize(255);
	}
}
