create table orders
(
    id         BIGSERIAL PRIMARY KEY,
    status     VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);