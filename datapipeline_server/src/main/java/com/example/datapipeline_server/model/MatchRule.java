package com.example.datapipeline_server.model;

import java.util.Locale;
import java.util.stream.Stream;

/**
 * Pluggable strategy that converts a {@link RawIdentityRow} into zero or more
 * {@link MatchKey}s. The correlation pipeline applies every rule to every
 * row; new rules can be added without touching pipeline code.
 *
 * <p>Built-in rules are exposed as static factories. Compose them in
 * {@code CorrelationPipeline}:
 *
 * <pre>{@code
 * var pipeline = new CorrelationPipeline(List.of(
 *     MatchRule.emailExact(),
 *     MatchRule.nationalIdExact(),
 *     MatchRule.nameDobExact(),
 *     MatchRule.phoneDobFuzzy()
 * ), 0.7);
 * }</pre>
 */
@FunctionalInterface
public interface MatchRule {

    Stream<MatchKey> keysFor(RawIdentityRow row);

    default String name() { return getClass().getSimpleName(); }

    // ---- Built-in rules ----

    /** Exact email after lowercase + trim. Deterministic. */
    static MatchRule emailExact() {
        return row -> row.email().stream()
                .map(e -> new MatchKey("EMAIL",
                        e.toLowerCase(Locale.ROOT).trim(), true));
    }

    /** Exact national ID (PAN/Aadhaar/SSN). Deterministic. */
    static MatchRule nationalIdExact() {
        return row -> row.nationalId().stream()
                .map(n -> new MatchKey("NATIONAL_ID",
                        n.toUpperCase(Locale.ROOT).replaceAll("\\s+", ""), true));
    }

    /** Exact name + DOB combination. Deterministic. */
    static MatchRule nameDobExact() {
        return row -> {
            if (row.fullName().isEmpty() || row.dateOfBirth().isEmpty()) {
                return Stream.empty();
            }
            var key = row.fullName().get().toLowerCase(Locale.ROOT)
                    .replaceAll("\\s+", " ").trim()
                    + "|" + row.dateOfBirth().get();
            return Stream.of(new MatchKey("NAME_DOB", key, true));
        };
    }

    /**
     * Phone + DOB. Probabilistic — shared phone+DOB across two clusters
     * is a strong duplicate signal but not proof (cohabitants, family
     * accounts, data-entry typos, mule networks).
     */
    static MatchRule phoneDobFuzzy() {
        return row -> {
            if (row.phone().isEmpty() || row.dateOfBirth().isEmpty()) {
                return Stream.empty();
            }
            var key = row.phone().get().replaceAll("\\D", "")
                    + "|" + row.dateOfBirth().get();
            return Stream.of(new MatchKey("PHONE_DOB", key, false));
        };
    }
}
