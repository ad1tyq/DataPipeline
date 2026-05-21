package com.example.datapipeline_server.model;

import java.util.Objects;

/**
 * A blocking/matching key derived from a {@link RawIdentityRow} by a
 * {@link MatchRule}. Two rows that produce the same {@code MatchKey} are
 * candidates to belong to the same human.
 *
 * <p>{@link #deterministic()} controls the pipeline's treatment:
 * <ul>
 *   <li><b>true</b>  — exact-match keys (email, national ID, name+DOB). Rows
 *       sharing the key are <i>unioned</i> into the same master cluster.</li>
 *   <li><b>false</b> — probabilistic keys (phone+DOB, fuzzy-name+city). Rows
 *       sharing the key but living in different clusters are emitted as
 *       {@link IdentityEdge.CrossCorrelation} edges for human review.</li>
 * </ul>
 */
public record MatchKey(String kind, String value, boolean deterministic) {
    public MatchKey {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
    }
}
