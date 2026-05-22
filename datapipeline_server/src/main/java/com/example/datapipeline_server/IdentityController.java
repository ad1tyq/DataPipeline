package com.example.datapipeline_server;

import com.example.datapipeline_server.model.*;
import com.example.datapipeline_server.pipeline.CorrelationPipeline;
import com.example.datapipeline_server.source.JdbcSourceAdapter;
import com.example.datapipeline_server.source.SourceAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.sql.DataSource;

// import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/identity")
public class IdentityController {

    private final CorrelationPipeline pipeline;
    private final DataSource dataSource; // The live database connection

    // Spring Boot automatically injects the H2 database into this constructor
    public IdentityController(DataSource dataSource) {
        this.pipeline = CorrelationPipeline.withDefaults();
        this.dataSource = dataSource;
    }

    @GetMapping("/graph")
    public GraphResponseDTO getIdentityGraph() {
        
        // 1. Map the live SQL tables to the engine
        List<SourceAdapter> adapters = List.of(
                
                // Read from Core Banking System
                new JdbcSourceAdapter("CORE_CBS", dataSource,
                        "SELECT emp_id, employee_name, work_email_addr, contact_mobile, date_of_birth, pan_number, branch_code, last_modified_ts FROM cbs_db.employee_master",
                        (rs, source) -> RawIdentityRow.builder()
                                .sourceSystem(source)
                                .externalId(rs.getString("emp_id"))
                                .fullName(rs.getString("employee_name"))
                                .email(rs.getString("work_email_addr"))
                                .phone(rs.getString("contact_mobile"))
                                .dateOfBirth(rs.getObject("date_of_birth", LocalDate.class))
                                .nationalId(rs.getString("pan_number"))
                                .attributes(Map.of("branch", rs.getString("branch_code")))
                                .extractedAt(rs.getTimestamp("last_modified_ts").toInstant())
                                .build()
                ),

                // Read from CRM System
                new JdbcSourceAdapter("CRM", dataSource,
                        "SELECT contact_uuid, full_name, primary_email, mobile_phone, birthdate, tax_id, region, updated_at FROM crm_db.contacts",
                        (rs, source) -> RawIdentityRow.builder()
                                .sourceSystem(source)
                                .externalId(rs.getString("contact_uuid"))
                                .fullName(rs.getString("full_name"))
                                .email(rs.getString("primary_email"))
                                .phone(rs.getString("mobile_phone"))
                                .dateOfBirth(rs.getObject("birthdate", LocalDate.class))
                                .nationalId(rs.getString("tax_id"))
                                .attributes(Map.of("region", rs.getString("region")))
                                .extractedAt(rs.getTimestamp("updated_at").toInstant())
                                .build()
                )
        );

        // 2. Run the pipeline on the live SQL data
        IdentityGraph rawGraph = pipeline.build(adapters);

        // 3. Flatten the graph for React (Same logic as before)
        List<NodeDTO> reactNodes = new ArrayList<>();
        List<EdgeDTO> reactEdges = new ArrayList<>();

        for (MasterIdentity master : rawGraph.masters()) {
            reactNodes.add(new NodeDTO(master.id().toString(), master.displayName() + " (Master)", "master", master.survivedAttributes()));
            for (RawIdentityRow subRow : master.subIdentities()) {
                reactNodes.add(new NodeDTO(subRow.id().toString(), subRow.fullName().orElse("Unknown") + " (" + subRow.sourceSystem() + ")", "raw_source", subRow.attributes()));
            }
        }
        for (IdentityEdge edge : rawGraph.edges()) {
            reactEdges.add(new EdgeDTO(edge.from().toString(), edge.to().toString(), edge.label()));
        }

        return new GraphResponseDTO(reactNodes, reactEdges);
    }
}

// --- Data Transfer Objects (DTOs) ---
// These records shape the final JSON output.
record NodeDTO(String id, String label, String group, Object data) {}
record EdgeDTO(String source, String target, String label) {}
record GraphResponseDTO(List<NodeDTO> nodes, List<EdgeDTO> edges) {}
