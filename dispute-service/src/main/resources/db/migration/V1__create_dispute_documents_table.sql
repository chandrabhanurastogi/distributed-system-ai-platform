CREATE EXTENSION IF NOT EXISTS vector;

create table dispute_documents
(
    id        BIGSERIAL PRIMARY KEY,
    text      TEXT NOT NULL,
    embedding vector(768) NOT NULL
);
