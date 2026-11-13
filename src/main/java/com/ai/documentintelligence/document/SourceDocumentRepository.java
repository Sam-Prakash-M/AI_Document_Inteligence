package com.ai.documentintelligence.document;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Status transitions use targeted UPDATE statements rather than {@code save(entity)}:
 * if a document is deleted while it is being ingested, the update simply affects
 * 0 rows instead of re-inserting the deleted row through a JPA merge.
 */
public interface SourceDocumentRepository extends JpaRepository<SourceDocument, UUID> {

	Page<SourceDocument> findByOwner(String owner, Pageable pageable);

	Optional<SourceDocument> findByIdAndOwner(UUID id, String owner);

	Optional<SourceDocument> findFirstByOwnerAndSha256AndStatusNot(String owner, String sha256, DocumentStatus status);

	@Transactional
	@Modifying
	@Query("""
			update SourceDocument d
			   set d.status = com.ai.documentintelligence.document.DocumentStatus.PROCESSING,
			       d.errorMessage = null, d.updatedAt = CURRENT_TIMESTAMP
			 where d.id = :id""")
	int markProcessing(UUID id);

	@Transactional
	@Modifying
	@Query("""
			update SourceDocument d
			   set d.status = com.ai.documentintelligence.document.DocumentStatus.READY,
			       d.chunkCount = :chunkCount, d.errorMessage = null, d.updatedAt = CURRENT_TIMESTAMP
			 where d.id = :id""")
	int markReady(UUID id, int chunkCount);

	@Transactional
	@Modifying
	@Query("""
			update SourceDocument d
			   set d.status = com.ai.documentintelligence.document.DocumentStatus.FAILED,
			       d.chunkCount = 0, d.errorMessage = :reason, d.updatedAt = CURRENT_TIMESTAMP
			 where d.id = :id""")
	int markFailed(UUID id, String reason);

	/** Used at startup: jobs that were in flight when the process died can never finish. */
	@Transactional
	@Modifying
	@Query("""
			update SourceDocument d
			   set d.status = com.ai.documentintelligence.document.DocumentStatus.FAILED,
			       d.errorMessage = :reason, d.updatedAt = CURRENT_TIMESTAMP
			 where d.status in :statuses""")
	int failAllInStatus(Collection<DocumentStatus> statuses, String reason);
}
