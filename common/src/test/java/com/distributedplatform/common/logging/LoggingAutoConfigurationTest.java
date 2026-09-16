package com.distributedplatform.common.logging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LoggingAutoConfiguration.class));

    @Test
    void autoConfiguration_registersCorrelationIdFilter() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CorrelationIdFilter.class);
        });
    }
}
