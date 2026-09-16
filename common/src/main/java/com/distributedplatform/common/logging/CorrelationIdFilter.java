package com.distributedplatform.common.logging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

public class CorrelationIdFilter implements Filter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String incoming = (request instanceof HttpServletRequest httpRequest)
                ? httpRequest.getHeader(CORRELATION_ID_HEADER)
                : null;
        String correlationId = (incoming != null && !incoming.isBlank())
                ? incoming
                : UUID.randomUUID().toString();
        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
        }
        try {
            MDC.put(MDC_KEY, correlationId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
