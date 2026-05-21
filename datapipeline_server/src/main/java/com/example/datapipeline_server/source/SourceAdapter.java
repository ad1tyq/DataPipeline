package com.example.datapipeline_server.source;

import com.example.datapipeline_server.model.RawIdentityRow;

import java.util.stream.Stream;

/**
 * SPI for pulling account data from one tenant/sub application and emitting
 * canonical {@link RawIdentityRow} instances. This is the boundary between
 * the master identity hub and the surrounding tenant systems.
 *
 * <p>Implementations may back the stream with:
 * <ul>
 *   <li>a JDBC cursor (the common case — see {@link JdbcSourceAdapter})</li>
 *   <li>a paginated REST/gRPC client</li>
 *   <li>a Kafka topic consumed up to a snapshot offset</li>
 *   <li>a CDC stream (Debezium/Goldengate)</li>
 *   <li>in-memory test fixtures (see {@link InMemorySourceAdapter})</li>
 * </ul>
 *
 * <p>The returned {@code Stream} is consumed once. It MAY be lazy; the
 * pipeline calls {@link AutoCloseable#close()} on the adapter after
 * consumption so cursor-backed streams can release their connections.
 */
public interface SourceAdapter extends AutoCloseable {

    /** Stable identifier for this source system (e.g. {@code "CORE_CBS"}). */
    String sourceSystem();

    /** A single-use stream of canonical rows. */
    Stream<RawIdentityRow> stream();

    @Override
    default void close() { /* no-op for adapters that don't hold resources */ }
}
