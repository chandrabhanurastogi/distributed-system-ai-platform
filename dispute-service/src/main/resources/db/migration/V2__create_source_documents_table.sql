create table source_documents
(
    id                BIGSERIAL PRIMARY KEY,
    source_identifier VARCHAR(500) NOT NULL UNIQUE,
    content_hash      VARCHAR(64)  NOT NULL,
    ingested_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

alter table dispute_documents
    add column source_document_id BIGINT NULL REFERENCES source_documents (id);
