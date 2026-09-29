package com.distributedplatform.disputeservice.service;

import com.distributedplatform.common.llm.ChatMessage;
import com.distributedplatform.common.llm.LlmClient;
import com.distributedplatform.common.llm.LlmResponse;
import com.distributedplatform.disputeservice.dao.DisputeDocumentRepository;
import com.distributedplatform.disputeservice.embedding.OllamaEmbeddingService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClaimClassificationService {

    private static final String EMBEDDING_MODEL = "nomic-embed-text";
    // Not yet measured against real retrieval quality - a placeholder, same as
    // MAX_CHUNK_SIZE was in DisputeDocumentIngestionService.
    private static final int TOP_K = 3;

    private final OllamaEmbeddingService embeddingService;
    private final DisputeDocumentRepository disputeDocumentRepository;
    private final LlmClient llmClient;

    public ClaimClassificationService(OllamaEmbeddingService embeddingService,
                                       DisputeDocumentRepository disputeDocumentRepository,
                                       LlmClient llmClient) {
        this.embeddingService = embeddingService;
        this.disputeDocumentRepository = disputeDocumentRepository;
        this.llmClient = llmClient;
    }

    public ClassificationResult classify(String claimText) {
        double[] claimEmbedding = embeddingService.embed(EMBEDDING_MODEL, claimText);

        List<String> retrievedContext = disputeDocumentRepository.findNearest(claimEmbedding, TOP_K);

        LlmResponse response = llmClient.chat(buildPrompt(claimText, retrievedContext));

        return parseResponse(response.content());
    }

    private List<ChatMessage> buildPrompt(String claimText, List<String> retrievedContext) {
        String context = String.join("\n\n---\n\n", retrievedContext);

        String systemPrompt = """
                You are a chargeback dispute classifier. You will be given relevant excerpts \
                from the cardholder dispute policy, followed by a customer's claim. Classify \
                the claim under exactly one network reason code mentioned in the policy \
                excerpts (for example 10.4, 13.1, or 13.3). Base your classification only on \
                the provided policy excerpts, not on general knowledge, and explicitly note \
                if any detail in the claim conflicts with the requirements for the code you chose.

                Respond in exactly this format, with nothing else:
                REASON_CODE: <code>
                EXPLANATION: <one or two sentences>

                Example:
                REASON_CODE: 13.1
                EXPLANATION: The goods were paid for but never delivered by the expected date.""";

        String userPrompt = """
                Policy excerpts:
                %s

                Customer claim:
                %s""".formatted(context, claimText);

        return List.of(
                new ChatMessage("system", systemPrompt),
                new ChatMessage("user", userPrompt)
        );
    }

    private ClassificationResult parseResponse(String content) {
        int reasonCodeIndex = content.indexOf("REASON_CODE:");

        if (reasonCodeIndex == -1) {
            // Real, observed failure mode with llama3.2: it sometimes ignores the
            // requested labels entirely and just writes the code on the first line
            // followed by the explanation - a known cost of plain-text parsing over
            // structured output, handled here rather than failing outright.
            List<String> lines = content.lines().toList();
            String reasonCode = lines.isEmpty() ? "" : lines.get(0).trim();
            String explanation = lines.size() > 1
                    ? String.join(" ", lines.subList(1, lines.size())).trim()
                    : "";
            return new ClassificationResult(reasonCode, explanation);
        }

        int explanationIndex = content.indexOf("EXPLANATION:");
        if (explanationIndex == -1) {
            String reasonCode = content.substring(reasonCodeIndex + "REASON_CODE:".length()).trim();
            return new ClassificationResult(reasonCode, "");
        }

        String reasonCode = content.substring(reasonCodeIndex + "REASON_CODE:".length(), explanationIndex).trim();
        String explanation = content.substring(explanationIndex + "EXPLANATION:".length()).trim();

        return new ClassificationResult(reasonCode, explanation);
    }
}
