package com.example.datapipeline_server.model;

import java.time.Instant;
import java.util.*;

/**
 * The pipeline's output — the graph snapshot to publish to the master
 * identity store and downstream consumers. Immutable; safe to fan out to
 * multiple sinks (Neo4j, Kafka, REST projection cache).
 */
public record IdentityGraph(
        List<MasterIdentity> masters,
        List<IdentityEdge> edges,
        Instant builtAt
) {
    public IdentityGraph {
        masters = List.copyOf(masters);
        edges   = List.copyOf(edges);
    }
}
