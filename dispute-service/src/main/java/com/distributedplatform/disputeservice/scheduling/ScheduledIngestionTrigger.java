package com.distributedplatform.disputeservice.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class ScheduledIngestionTrigger {

    private static final Logger log = LoggerFactory.getLogger(ScheduledIngestionTrigger.class);

    private final DisputeCorpusIngestionRunner runner;
    private final Path sourceDirectory;

    public ScheduledIngestionTrigger(DisputeCorpusIngestionRunner runner,
                                      @Value("${dispute.ingestion.source-directory}") String sourceDirectory) {
        this.runner = runner;
        this.sourceDirectory = Path.of(sourceDirectory);
    }

    @Scheduled(cron = "${dispute.ingestion.cron}")
    public void triggerScheduledIngestion() {
        log.info("Scheduled ingestion triggered for source directory {}", sourceDirectory);
        runner.run(sourceDirectory);
    }
}
