package com.ai.documentintelligence.vector;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ai.documentintelligence.config.AppProperties;

/**
 * pgvector access for document chunks, via plain JDBC.
 * <p>
 * Similarity is cosine: {@code <=>} is pgvector's cosine <i>distance</i>, so
 * {@code score = 1 - distance}. Ordering by the raw distance expression lets
 * PostgreSQL use the HNSW index ({@code vector_cosine_ops}) created in V1.
 */
@Repository
public class ChunkRepository {

	private static final Logger log = LoggerFactory.getLogger(ChunkRepository.class);

	private final NamedParameterJdbcTemplate jdbc;
	private final int dimension;

	public ChunkRepository(NamedParameterJdbcTemplate jdbc, AppProperties props) {
		this.jdbc = jdbc;
		this.dimension = props.ai().embedding().dimension();
	}

	/** Atomically replaces all chunks of a document. */
	@Transactional
	public void replaceChunks(UUID documentId, String owner, List<NewChunk> chunks) {
		deleteByDocument(documentId);
		SqlParameterSource[] batch = chunks.stream().map(c -> {
			if (c.embedding().length != dimension) {
				throw new IllegalStateException("Embedding has " + c.embedding().length
						+ " dimensions but app.ai.embedding.dimension is " + dimension);
			}
			return new MapSqlParameterSource()
					.addValue("documentId", documentId)
					.addValue("owner", owner)
					.addValue("chunkIndex", c.chunkIndex())
					.addValue("content", c.content())
					.addValue("embedding", VectorFormat.toLiteral(c.embedding()));
		}).toArray(SqlParameterSource[]::new);

		jdbc.batchUpdate("""
				INSERT INTO document_chunks (document_id, owner, chunk_index, content, embedding)
				VALUES (:documentId, :owner, :chunkIndex, :content, CAST(:embedding AS vector))""", batch);
	}

	public void deleteByDocument(UUID documentId) {
		jdbc.update("DELETE FROM document_chunks WHERE document_id = :id", new MapSqlParameterSource("id", documentId));
	}

	/**
	 * Top-K most similar chunks of the owner's READY documents.
	 *
	 * @param documentIds optional filter; null or empty searches all of the owner's documents
	 */
	public List<ChunkMatch> search(String owner, float[] queryEmbedding, int topK, double minScore,
			Collection<UUID> documentIds) {
		var params = new MapSqlParameterSource()
				.addValue("owner", owner)
				.addValue("query", VectorFormat.toLiteral(queryEmbedding))
				.addValue("topK", topK);

		String docFilter = "";
		if (documentIds != null && !documentIds.isEmpty()) {
			docFilter = " AND c.document_id IN (:documentIds)";
			params.addValue("documentIds", documentIds);
		}

		String sql = """
				SELECT c.document_id, d.filename, c.chunk_index, c.content,
				       1 - (c.embedding <=> CAST(:query AS vector)) AS score
				  FROM document_chunks c
				  JOIN documents d ON d.id = c.document_id
				 WHERE c.owner = :owner
				   AND d.status = 'READY'%s
				 ORDER BY c.embedding <=> CAST(:query AS vector)
				 LIMIT :topK""".formatted(docFilter);

		return jdbc.query(sql, params, (rs, i) -> new ChunkMatch(
						rs.getObject("document_id", UUID.class),
						rs.getString("filename"),
						rs.getInt("chunk_index"),
						rs.getString("content"),
						rs.getDouble("score")))
				.stream()
				.filter(m -> m.score() >= minScore)
				.toList();
	}

	/**
	 * Fails fast when the configured embedding dimension does not match the column the
	 * schema was created with (e.g. switching from OpenAI 1536-d to Ollama 768-d).
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void verifyDimension() {
		Integer dbDimension = jdbc.getJdbcTemplate().queryForObject("""
				SELECT a.atttypmod FROM pg_attribute a
				 WHERE a.attrelid = 'document_chunks'::regclass AND a.attname = 'embedding'""", Integer.class);
		if (dbDimension != null && dbDimension > 0 && dbDimension != dimension) {
			throw new IllegalStateException("Database column document_chunks.embedding is vector(" + dbDimension
					+ ") but app.ai.embedding.dimension=" + dimension
					+ ". Use the matching embedding model, or recreate the database (see README: 'Changing the embedding model').");
		}
		log.info("pgvector column dimension verified: {}", dimension);
	}
}
