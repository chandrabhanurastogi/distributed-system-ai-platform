create table inventory_items
(
    id         BIGSERIAL PRIMARY KEY,
    sku        VARCHAR(50) NOT NULL UNIQUE,
    quantity   INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);