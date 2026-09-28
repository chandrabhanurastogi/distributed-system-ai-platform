package com.distributedplatform.disputeservice.scheduling;

import java.util.List;

public record IngestionRunSummary(String ingestionRunId, int succeededCount, int skippedCount, int failedCount,
                                   List<String> failedFiles) {
}
