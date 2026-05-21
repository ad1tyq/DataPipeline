package com.example.datapipeline_server.pipeline;

import com.example.datapipeline_server.model.*;
import com.example.datapipeline_server.source.SourceAdapter;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The five-stage streaming correlation pipeline. Acts as the bridge between
 * tenant/sub application source systems and the master identity store.
 *
 * <pre>
 *  [Source Adapters] -> Extract -> Derive keys -> Block -> Cluster -> Emit Graph
 *  (CBS, AD, Email,    parallel    MatchRules    invert   union-    masters +
 *   Trading, Risk,     virtual     fan-out       index    find       aggregation
 *   Loans, ...)        threads                                       + cross-
 *                                                                    correlation
 *                                                                    edges
 * </pre>
 *
 * <p>The pipeline is stateless; a single instance is safe to reuse across
 * multiple {@link #build(List)} invocations and across threads.
 */
public final class CorrelationPipeline {

    /** Sources whose values are preferred when surviving the master's display name. */
    private static final Map<String, Integer> SOURCE_PRIORITY = Map.of(
            "CORE_CBS", 1,
            "AD_LDAP",  2,
            "EMAIL",    3
    );

    private final List<MatchRule> matchRules;
    private final double crossCorrelationThreshold;

    public CorrelationPipeline(List<MatchRule> matchRules,
                               double crossCorrelationThreshold) {
        this.matchRules = List.copyOf(matchRules);
        this.crossCorrelationThreshold = crossCorrelationThreshold;
    }

    /** Sensible defaults: email+nationalId+nameDob deterministic, phone+DOB probabilistic. */
    public static CorrelationPipeline withDefaults() {
        return new CorrelationPipeline(List.of(
                MatchRule.emailExact(),
                MatchRule.nationalIdExact(),
                MatchRule.nameDobExact(),
                MatchRule.phoneDobFuzzy()
        ), 0.7);
    }

    public IdentityGraph build(List<SourceAdapter> adapters) {

        // ─── Stage 1 ── Extract in parallel via virtual threads ──────────
        var allRows = fetchInParallel(adapters);

        // ─── Stage 2 ── Derive every match key for every row ──────────────
        record KeyedRow(MatchKey key, RawIdentityRow row) { }

        var keyedRows = allRows.stream()
                .flatMap(row -> matchRules.stream()
                        .flatMap(rule -> rule.keysFor(row))
                        .map(key -> new KeyedRow(key, row)))
                .toList();

        // ─── Stage 3 ── Block: invert the (key, row) pairs into key -> [rows]
        var blockingIndex = keyedRows.stream()
                .collect(Collectors.groupingBy(
                        KeyedRow::key,
                        Collectors.mapping(KeyedRow::row, Collectors.toList())));

        // ─── Stage 4 ── Cluster on deterministic key co-occurrence ────────
        var uf = new UnionFind<UUID>();
        allRows.forEach(r -> uf.makeSet(r.id()));

        blockingIndex.entrySet().stream()
                .filter(e -> e.getKey().deterministic() && e.getValue().size() > 1)
                .forEach(e -> {
                    var rows = e.getValue();
                    var pivot = rows.getFirst().id();
                    rows.stream().skip(1).forEach(r -> uf.union(pivot, r.id()));
                });

        // ─── Stage 5a ── Survive canonical values per cluster -> masters ──
        var masters = allRows.stream()
                .collect(Collectors.groupingBy(r -> uf.find(r.id())))
                .entrySet().stream()
                .map(e -> survive(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(MasterIdentity::displayName))
                .toList();

        // ─── Stage 5b ── Aggregation edges fall out of the clusters ───────
        List<IdentityEdge> aggregationEdges = masters.stream()
                .flatMap(m -> m.subIdentities().stream()
                        .<IdentityEdge>map(s -> new IdentityEdge.Aggregates(m.id(), s.id())))
                .toList();

        // ─── Stage 5c ── Cross-cluster correlations from probabilistic blocks
        var crossCorrelationEdges = probabilisticCrossEdges(blockingIndex, uf);

        return new IdentityGraph(
                masters,
                Stream.concat(aggregationEdges.stream(), crossCorrelationEdges.stream()).toList(),
                Instant.now()
        );
    }

    /**
     * Fan out one virtual-thread task per source adapter, then join. Virtual
     * threads make this cheap even with 50+ adapters — each one blocks on its
     * own JDBC cursor without burning a platform thread.
     */
    private List<RawIdentityRow> fetchInParallel(List<SourceAdapter> adapters) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return adapters.stream()
                    .map(adapter -> CompletableFuture.supplyAsync(() -> {
                        try (adapter) {                       // closes cursor on completion
                            return adapter.stream().toList();
                        } catch (Exception e) {
                            throw new CompletionException(
                                    "Adapter " + adapter.sourceSystem() + " failed", e);
                        }
                    }, executor))
                    .toList()                                  // submit ALL before any join
                    .stream()
                    .flatMap(f -> f.join().stream())
                    .toList();
        }
    }

    /**
     * Survivorship: pick the best value per attribute across the cluster.
     * Strategy here is source-priority for display name (Core Banking wins),
     * most-recent-by-extractedAt for the attribute bag, first-non-empty for
     * employee ID. Swap this method for your bank's data-stewardship policy.
     */
    private MasterIdentity survive(UUID clusterRoot, List<RawIdentityRow> cluster) {

        var displayName = cluster.stream()
                .filter(r -> r.fullName().isPresent())
                .min(Comparator.comparingInt(r ->
                        SOURCE_PRIORITY.getOrDefault(r.sourceSystem(), 99)))
                .flatMap(RawIdentityRow::fullName)
                .orElse("Unknown");

        var employeeId = cluster.stream()
                .filter(r -> "CORE_CBS".equals(r.sourceSystem()))
                .findFirst()
                .map(RawIdentityRow::externalId);

        var survivedAttributes = cluster.stream()
                .sorted(Comparator.comparing(RawIdentityRow::extractedAt))
                .flatMap(r -> r.attributes().entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (older, newer) -> newer));            // most recent wins

        return new MasterIdentity(
                clusterRoot, displayName, employeeId,
                cluster, survivedAttributes, Instant.now()
        );
    }

    /**
     * For each probabilistic block, emit edges between every pair of rows
     * that ended up in different clusters. Pairs are normalized (smaller-UUID
     * first) and merged across rules so two rules firing on the same pair
     * contribute a single edge with the union of rule kinds and an additive
     * score (capped at 1.0).
     */
    private List<IdentityEdge.CrossCorrelation> probabilisticCrossEdges(
            Map<MatchKey, List<RawIdentityRow>> blockingIndex,
            UnionFind<UUID> uf) {

        record Pair(UUID a, UUID b) {
            static Pair of(UUID x, UUID y) {
                return x.compareTo(y) < 0 ? new Pair(x, y) : new Pair(y, x);
            }
        }

        return blockingIndex.entrySet().stream()
                .filter(e -> !e.getKey().deterministic() && e.getValue().size() > 1)
                .<IdentityEdge.CrossCorrelation>mapMulti((entry, sink) -> {
                    var ruleKind = entry.getKey().kind();
                    var rows = entry.getValue();
                    for (int i = 0; i < rows.size(); i++) {
                        for (int j = i + 1; j < rows.size(); j++) {
                            var left  = rows.get(i);
                            var right = rows.get(j);
                            if (!uf.find(left.id()).equals(uf.find(right.id()))) {
                                sink.accept(new IdentityEdge.CrossCorrelation(
                                        left.id(), right.id(), Set.of(ruleKind), 0.85));
                            }
                        }
                    }
                })
                .collect(Collectors.toMap(
                        e -> Pair.of(e.from(), e.to()),
                        e -> e,
                        (e1, e2) -> new IdentityEdge.CrossCorrelation(
                                e1.from(), e1.to(),
                                Stream.concat(e1.matchedOn().stream(),
                                              e2.matchedOn().stream())
                                      .collect(Collectors.toUnmodifiableSet()),
                                Math.min(1.0, e1.score() + 0.1))))
                .values().stream()
                .filter(e -> e.score() >= crossCorrelationThreshold)
                .toList();
    }
}
