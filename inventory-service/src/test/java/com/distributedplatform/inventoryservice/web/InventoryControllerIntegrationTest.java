package com.distributedplatform.inventoryservice.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InventoryControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private String sku;

    @BeforeEach
    void seedInventoryItem() {
        sku = "sku-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO inventory_items (sku, quantity) VALUES (:sku, :quantity)",
                new MapSqlParameterSource().addValue("sku", sku).addValue("quantity", 5));
    }

    @Test
    void getBySku_returnsSeededItem() throws Exception {
        mockMvc.perform(get("/inventory/{sku}", sku))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.quantity").value(5));
    }

    @Test
    void getBySku_returns404WhenMissing() throws Exception {
        mockMvc.perform(get("/inventory/{sku}", "does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reserve_decrementsQuantityOnSuccess() throws Exception {
        mockMvc.perform(post("/inventory/{sku}/reserve", sku)
                        .contentType(APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void reserve_returns409WhenInsufficientStock() throws Exception {
        mockMvc.perform(post("/inventory/{sku}/reserve", sku)
                        .contentType(APPLICATION_JSON)
                        .content("{\"quantity\":10}"))
                .andExpect(status().isConflict());
    }

    @Test
    void reserve_returns400ForNonPositiveQuantity() throws Exception {
        mockMvc.perform(post("/inventory/{sku}/reserve", sku)
                        .contentType(APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }
}
