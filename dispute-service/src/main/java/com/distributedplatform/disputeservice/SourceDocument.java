package com.distributedplatform.disputeservice;

import java.time.LocalDateTime;

public record SourceDocument(Long id, String sourceIdentifier, String contentHash, LocalDateTime ingestedAt) {
}
