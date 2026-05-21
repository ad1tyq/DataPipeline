package com.example.datapipeline_server.model;

import java.time.Instant;
import java.util.*;

/**
 * The "golden record" — one surviving identity per cluster of correlated
 * source rows. This is what gets written to the master identity store
 * (Neo4j, JanusGraph, Postgres+graph extension, etc.) and exposed to
 * downstream tenant/sub applications.
 */
public record MasterIdentity(
        UUID id,
        String displayName,
        Optional<String> employeeId,
        List<RawIdentityRow> subIdentities,
        Map<String, String> survivedAttributes,
        Instant resolvedAt
) {
    public MasterIdentity {
        Objects.requireNonNull(id);
        Objects.requireNonNull(displayName);
        subIdentities      = List.copyOf(subIdentities);
        survivedAttributes = Map.copyOf(survivedAttributes);
    }
}
