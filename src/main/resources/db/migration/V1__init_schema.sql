-- Schema for the RAG store.
-- ${embedding_dimension} is a Flyway placeholder bound to app.ai.embedding.dimension.
-- It is fixed when this migration first runs; changing embedding models later needs a
-- new database (or a migration that re-creates the column and re-ingests documents).

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id            UUID          PRIMARY KEY,
    owner         VARCHAR(100)  NOT NULL,
    filename      VARCHAR(512)  NOT NULL,
    content_type  VARCHAR(255),
    size_bytes    BIGINT        NOT NULL,
    sha256        VARCHAR(64)   NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    error_message TEXT,
    chunk_count   INTEGER       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_documents_owner_created ON documents (owner, created_at DESC);
CREATE INDEX idx_documents_owner_sha256  ON documents (owner, sha256);

CREATE TABLE document_chunks (
    id           BIGSERIAL     PRIMARY KEY,
    document_id  UUID          NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    owner        VARCHAR(100)  NOT NULL,
    chunk_index  INTEGER       NOT NULL,
    content      TEXT          NOT NULL,
    embedding    vector(${embedding_dimension}) NOT NULL,
    UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_chunks_owner ON document_chunks (owner);

-- Approximate nearest-neighbour index for cosine distance (the <=> operator).
CREATE INDEX idx_chunks_embedding_hnsw
    ON document_chunks USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
