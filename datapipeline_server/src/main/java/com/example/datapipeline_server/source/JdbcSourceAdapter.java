package com.example.datapipeline_server.source;

import com.example.datapipeline_server.model.RawIdentityRow;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Production adapter for relational sources. Opens a server-side cursor
 * (forward-only, read-only, configurable fetch size) and lazily emits rows.
 * The {@code DataSource} should be HikariCP or similar — the pipeline runs
 * many adapters concurrently on virtual threads, so the pool sizing matters.
 *
 * <p>Wiring example (Spring Boot — Birla Bank Core CBS read replica):
 *
 * <pre>{@code
 * @Bean
 * SourceAdapter coreCbsAdapter(@Qualifier("cbsReadReplica") DataSource ds) {
 *     return new JdbcSourceAdapter(
 *         "CORE_CBS",
 *         ds,
 *         """
 *         select emp_id, full_name, email, mobile, dob, pan, branch_code, last_modified
 *         from   employee_master
 *         where  active_flag = 'Y'
 *         """,
 *         (rs, source) -> RawIdentityRow.builder()
 *             .sourceSystem(source)
 *             .externalId(rs.getString("emp_id"))
 *             .fullName(rs.getString("full_name"))
 *             .email(rs.getString("email"))
 *             .phone(rs.getString("mobile"))
 *             .dateOfBirth(rs.getObject("dob", LocalDate.class))
 *             .nationalId(rs.getString("pan"))
 *             .attributes(Map.of("branch", rs.getString("branch_code")))
 *             .extractedAt(rs.getTimestamp("last_modified").toInstant())
 *             .build());
 * }
 * }</pre>
 */
public final class JdbcSourceAdapter implements SourceAdapter {

    /** Functional interface that propagates {@link SQLException} (which {@link java.util.function.Function} cannot). */
    @FunctionalInterface
    public interface RowMapper {
        RawIdentityRow map(ResultSet rs, String sourceSystem) throws SQLException;
    }

    private final String sourceSystem;
    private final DataSource dataSource;
    private final String selectSql;
    private final RowMapper rowMapper;
    private final int fetchSize;

    // Stateful: held across stream() / close() to release on stream-close.
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;

    public JdbcSourceAdapter(String sourceSystem, DataSource dataSource,
                              String selectSql, RowMapper rowMapper) {
        this(sourceSystem, dataSource, selectSql, rowMapper, 1000);
    }

    public JdbcSourceAdapter(String sourceSystem, DataSource dataSource,
                              String selectSql, RowMapper rowMapper, int fetchSize) {
        this.sourceSystem = Objects.requireNonNull(sourceSystem);
        this.dataSource   = Objects.requireNonNull(dataSource);
        this.selectSql    = Objects.requireNonNull(selectSql);
        this.rowMapper    = Objects.requireNonNull(rowMapper);
        this.fetchSize    = fetchSize;
    }

    @Override public String sourceSystem() { return sourceSystem; }

    @Override
    public Stream<RawIdentityRow> stream() {
        try {
            connection = dataSource.getConnection();
            // Many drivers (Postgres, MySQL) only honor fetchSize when
            // auto-commit is off — otherwise the whole result set is buffered.
            connection.setAutoCommit(false);
            statement = connection.prepareStatement(selectSql,
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            statement.setFetchSize(fetchSize);
            resultSet = statement.executeQuery();

            var spliterator = new Spliterators.AbstractSpliterator<RawIdentityRow>(
                    Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL) {
                @Override
                public boolean tryAdvance(Consumer<? super RawIdentityRow> action) {
                    try {
                        if (!resultSet.next()) return false;
                        action.accept(rowMapper.map(resultSet, sourceSystem));
                        return true;
                    } catch (SQLException e) {
                        throw new IdentityExtractionException(
                                "Row mapping failed for " + sourceSystem, e);
                    }
                }
            };

            return StreamSupport.stream(spliterator, false)
                    .onClose(this::releaseResources);
        } catch (SQLException e) {
            releaseResources();
            throw new IdentityExtractionException(
                    "Failed to open cursor for " + sourceSystem, e);
        }
    }

    @Override
    public void close() {
        releaseResources();
    }

    private void releaseResources() {
        closeQuietly(resultSet);
        closeQuietly(statement);
        closeQuietly(connection);
        resultSet = null;
        statement = null;
        connection = null;
    }

    private static void closeQuietly(AutoCloseable r) {
        if (r == null) return;
        try { r.close(); } catch (Exception ignored) { /* logged elsewhere */ }
    }

    public static final class IdentityExtractionException extends RuntimeException {
        public IdentityExtractionException(String msg, Throwable cause) { super(msg, cause); }
    }
}
