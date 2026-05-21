package com.example.datapipeline_server;

import com.example.datapipeline_server.model.*;
import com.example.datapipeline_server.pipeline.CorrelationPipeline;
import com.example.datapipeline_server.source.InMemorySourceAdapter;
import com.example.datapipeline_server.source.SourceAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/identity")
public class IdentityController {

    private final CorrelationPipeline pipeline;

    public IdentityController() {
        this.pipeline = CorrelationPipeline.withDefaults();
    }

    @GetMapping("/graph")
    public GraphResponseDTO getIdentityGraph() {
        var now = Instant.now();

        // 1. Create a few dummy rows
        var priyaCBS = RawIdentityRow.builder()
                .sourceSystem("CORE_CBS").externalId("EMP-7832")
                .fullName("Priya Sharma")
                .email("priya.sharma@birlabank.in")
                .phone("+91-9876543210")
                .dateOfBirth(LocalDate.of(1992, 7, 22))
                .nationalId("BBBPS5678B")
                .attributes(Map.of("branch", "Mumbai"))
                .extractedAt(now).build();

        var priyaEmail = RawIdentityRow.builder()
                .sourceSystem("EMAIL").externalId("priya.s@")
                .fullName("Priya S.")
                .email("priya.sharma@birlabank.in")
                .phone("+91-9820001234")
                .dateOfBirth(LocalDate.of(1992, 7, 22))
                .extractedAt(now).build();

        // 2. Load them into the In-Memory adapters
        List<SourceAdapter> adapters = List.of(
                new InMemorySourceAdapter("CORE_CBS", List.of(priyaCBS)),
                new InMemorySourceAdapter("EMAIL", List.of(priyaEmail))
        );

        // 3. Run the pipeline to get the raw, nested graph
        IdentityGraph rawGraph = pipeline.build(adapters);

        // 4. Flatten the graph for the React frontend
        List<NodeDTO> reactNodes = new ArrayList<>();
        List<EdgeDTO> reactEdges = new ArrayList<>();

        // Extract nodes
        for (MasterIdentity master : rawGraph.masters()) {
            // Add the Golden Record (Master) node
            reactNodes.add(new NodeDTO(
                    master.id().toString(),
                    master.displayName() + " (Master)",
                    "master",
                    master.survivedAttributes()
            ));

            // Extract the nested raw sources into flat nodes
            for (RawIdentityRow subRow : master.subIdentities()) {
                reactNodes.add(new NodeDTO(
                        subRow.id().toString(),
                        subRow.fullName().orElse("Unknown") + " (" + subRow.sourceSystem() + ")",
                        "raw_source",
                        subRow.attributes()
                ));
            }
        }

        // Extract edges and fix the missing label bug
        for (IdentityEdge edge : rawGraph.edges()) {
            reactEdges.add(new EdgeDTO(
                    edge.from().toString(),
                    edge.to().toString(),
                    edge.label() // Explicitly grabs "AGGREGATES" or "POSSIBLE_DUPLICATE"
            ));
        }

        // 5. Return the flattened DTO
        return new GraphResponseDTO(reactNodes, reactEdges);
    }
}

// --- Data Transfer Objects (DTOs) ---
// These records shape the final JSON output.
record NodeDTO(String id, String label, String group, Object data) {}
record EdgeDTO(String source, String target, String label) {}
record GraphResponseDTO(List<NodeDTO> nodes, List<EdgeDTO> edges) {}
