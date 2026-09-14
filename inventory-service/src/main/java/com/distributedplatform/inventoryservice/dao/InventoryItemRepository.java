package com.distributedplatform.inventoryservice.dao;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryItemRepository{
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryItemRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public InventoryItemDto findById(long id) {
        String sql = "SELECT id, sku, quantity, created_at FROM inventory_items WHERE id = :id";
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        //TODO: Handle 404
        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new InventoryItemDto(
                rs.getLong("id"),
                rs.getString("sku"),
                rs.getInt("quantity"),
                rs.getTimestamp("created_at").toLocalDateTime()
        ));
    }

    public Long save(InventoryItemDto inventoryItemDto) {
        String sql = """
                INSERT INTO inventory_items (sku, quantity, created_at)
                VALUES (:sku, :quantity, :createdAt)
                RETURNING id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sku", inventoryItemDto.getSku())
                .addValue("quantity", inventoryItemDto.getQuantity())
                .addValue("createdAt", inventoryItemDto.getCreatedAt());

        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }


}
