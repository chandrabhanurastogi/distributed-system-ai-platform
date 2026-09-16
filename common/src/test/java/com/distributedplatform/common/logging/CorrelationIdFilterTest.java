package com.distributedplatform.common.logging;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void doFilter_generatesCorrelationIdWhenHeaderIsMissing() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcValueInsideChain = new AtomicReference<>();

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                mdcValueInsideChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            }
        };

        filter.doFilter(request, response, chain);

        String responseHeader = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertNotNull(responseHeader);
        assertFalse(responseHeader.isBlank());
        assertEquals(responseHeader, mdcValueInsideChain.get());
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY), "MDC should be cleared after filter completion");
    }

    @Test
    void doFilter_propagatesIncomingCorrelationIdWhenPresent() throws IOException, ServletException {
        String existingCorrelationId = "test-correlation-id-12345";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, existingCorrelationId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcValueInsideChain = new AtomicReference<>();

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                mdcValueInsideChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            }
        };

        filter.doFilter(request, response, chain);

        assertEquals(existingCorrelationId, response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
        assertEquals(existingCorrelationId, mdcValueInsideChain.get());
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY), "MDC should be cleared after filter completion");
    }
}
