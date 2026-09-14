package com.distributedplatform.orderservice.dao;

import com.distributedplatform.orderservice.Order;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
/*
Beyond marking the class for component scanning, it enables Spring's exception translation — a raw JDBC SQLException (vendor-specific, checked) gets rewritten into Spring's DataAccessException hierarchy (unchecked, vendor-agnostic).
 */
public class OrderRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OrderRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Order findById(long id) {
        String sql = "SELECT id, status, created_at FROM orders WHERE id = :id";
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        //TODO: Handle 404
        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Order(
                rs.getLong("id"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime()
        ));
    }

    public Long save(Order order) {
        String sql = """
                INSERT INTO orders (status, created_at)
                VALUES (:status, :createdAt)
                RETURNING id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", order.getStatus())
                .addValue("createdAt", order.getCreatedAt());

        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }
}
