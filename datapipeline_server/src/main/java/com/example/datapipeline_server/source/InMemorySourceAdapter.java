package com.example.datapipeline_server.source;

import com.example.datapipeline_server.model.RawIdentityRow;

import java.util.List;
import java.util.stream.Stream;

/**
 * Trivial adapter for tests, demos, and bootstrap loads. Holds rows in memory
 * and streams them on demand. Production deployments should use
 * {@link JdbcSourceAdapter} or a CDC/Kafka equivalent.
 */
public final class InMemorySourceAdapter implements SourceAdapter {

    private final String sourceSystem;
    private final List<RawIdentityRow> rows;

    public InMemorySourceAdapter(String sourceSystem, List<RawIdentityRow> rows) {
        this.sourceSystem = sourceSystem;
        this.rows = List.copyOf(rows);
    }

    @Override public String sourceSystem() { return sourceSystem; }
    @Override public Stream<RawIdentityRow> stream() { return rows.stream(); }
}
