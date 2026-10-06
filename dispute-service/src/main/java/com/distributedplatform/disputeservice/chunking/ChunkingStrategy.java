package com.distributedplatform.disputeservice.chunking;

import java.util.List;

/**
 * No call-time parameters, deliberately: each implementation is fully configured at
 * construction/bean time (size bound, overlap, similarity threshold, whatever it
 * needs), so a future strategy-comparison harness can hold a list of these and call
 * chunk(text) identically on every one, regardless of which knobs a given strategy
 * actually uses internally.
 */
public interface ChunkingStrategy {

    List<String> chunk(String text);
}
