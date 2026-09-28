package com.distributedplatform.disputeservice;

public record DisputeDocument(String text, double[] embedding, Long sourceDocumentId) {

    public DisputeDocument(String text, double[] embedding) {
        this(text, embedding, null);
    }
}
