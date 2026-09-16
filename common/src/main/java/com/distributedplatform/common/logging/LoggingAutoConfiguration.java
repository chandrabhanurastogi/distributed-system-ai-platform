package com.distributedplatform.common.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/*
Because com.distributedplatform.common is outside the package hierarchies of orderservice and inventoryservice, Spring Boot's default component scan will not find it.

To make a library's beans discoverable across multiple microservices without requiring each service to manually configure @ComponentScan("com.distributedplatform") or @Import(...), Spring Boot uses the Auto-Configuration mechanism:
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}
