package com.distributedplatform.disputeservice.scheduling;

import com.distributedplatform.disputeservice.service.DisputeDocumentIngestionService;
import com.distributedplatform.disputeservice.service.IngestionOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class DisputeCorpusIngestionRunner {

    private static final Logger log = LoggerFactory.getLogger(DisputeCorpusIngestionRunner.class);
    private static final String MDC_KEY = "ingestionRunId";

    private final SourceDocumentScanner scanner;
    private final DisputeDocumentIngestionService ingestionService;

    public DisputeCorpusIngestionRunner(SourceDocumentScanner scanner,
                                         DisputeDocumentIngestionService ingestionService) {
        this.scanner = scanner;
        this.ingestionService = ingestionService;
    }

    public IngestionRunSummary run(Path sourceDirectory) {
        String ingestionRunId = UUID.randomUUID().toString();

        try {
            MDC.put(MDC_KEY, ingestionRunId);

            List<Path> files;
            try {
                files = scanner.scan(sourceDirectory);
            } catch (IOException e) {
                log.error("Failed to scan source directory {}", sourceDirectory, e);
                return new IngestionRunSummary(ingestionRunId, 0, 0, 0, List.of());
            }

            int succeededCount = 0;
            int skippedCount = 0;
            List<String> failedFiles = new ArrayList<>();

            for (Path file : files) {
                String sourceIdentifier = file.getFileName().toString();
                try {
                    // Reading (and UTF-8 decoding) happens BEFORE reingestFromSource is
                    // called, so a failure here never enters its @Transactional
                    // boundary - failure isolation for a bad file stays a pure
                    // application-level concern, never a poisoned database transaction.
                    String content = Files.readString(file);
                    String contentHash = ContentHasher.sha256Hex(content);
                    IngestionOutcome outcome = ingestionService.reingestFromSource(sourceIdentifier, content, contentHash);
                    if (outcome == IngestionOutcome.SKIPPED_UNCHANGED) {
                        skippedCount++;
                    } else {
                        succeededCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to ingest source file {}", sourceIdentifier, e);
                    failedFiles.add(sourceIdentifier);
                }
            }

            IngestionRunSummary summary = new IngestionRunSummary(
                    ingestionRunId, succeededCount, skippedCount, failedFiles.size(), failedFiles);
            log.info("Ingestion run complete: runId={}, succeeded={}, skipped={}, failed={}, failedFiles={}",
                    ingestionRunId, succeededCount, skippedCount, failedFiles.size(), failedFiles);
            return summary;
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
