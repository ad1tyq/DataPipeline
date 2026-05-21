package com.example.datapipeline_server.model;

import java.util.*;

/**
 * Edges in the identity graph. Sealed so downstream sinks can use exhaustive
 * pattern-matching switches to dispatch (Neo4j {@code MERGE}, Kafka event,
 * etc.) without forgetting a case.
 *
 * <pre>{@code
 * String cypher = switch (edge) {
 *     case IdentityEdge.Aggregates a       -> mergeAggregates(a);
 *     case IdentityEdge.CrossCorrelation c -> mergeCrossCorr(c);
 * };
 * }</pre>
 */
public sealed interface IdentityEdge
        permits IdentityEdge.Aggregates, IdentityEdge.CrossCorrelation {

    UUID from();
    UUID to();
    String label();

    /**
     * Master -> sub-identity. Each cluster produces N of these (one per
     * sub-identity that aggregated into the master).
     */
    record Aggregates(UUID from, UUID to) implements IdentityEdge {
        @Override public String label() { return "AGGREGATES"; }
    }

    /**
     * Sub-identity -> sub-identity across different master clusters. This is
     * the "graph beats tree" payoff — it surfaces possible duplicates and
     * suspicious linkages that a hierarchical MDM model could never express.
     *
     * @param matchedOn which rule kinds fired (e.g. {@code [PHONE_DOB]})
     * @param score     confidence in {@code [0.0, 1.0]}
     */
    record CrossCorrelation(UUID from, UUID to, Set<String> matchedOn, double score)
            implements IdentityEdge {
        public CrossCorrelation {
            matchedOn = Set.copyOf(matchedOn);
        }
        @Override public String label() { return "POSSIBLE_DUPLICATE"; }
    }
}
