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
import java.util.LinkedHashMap;
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
                ),

                // 3. Read from Active Directory
                new JdbcSourceAdapter("AD_LDAP", dataSource,
                        "SELECT sam_account_name, display_name, user_principal_name, telephone_number, employee_id, department, title, office_location, when_changed FROM ad_db.ad_users",
                        (rs, source) -> RawIdentityRow.builder()
                                .sourceSystem(source)
                                .externalId(rs.getString("sam_account_name"))
                                .fullName(rs.getString("display_name"))
                                .email(rs.getString("user_principal_name"))
                                .phone(rs.getString("telephone_number"))
                                // Notice: AD does not track Date of Birth or National ID. 
                                // Do not attempt to map them, let them default to null.
                                .attributes(Map.of(
                                        "department", rs.getString("department") != null ? rs.getString("department") : "",
                                        "title", rs.getString("title") != null ? rs.getString("title") : "",
                                        "employee_id", rs.getString("employee_id") != null ? rs.getString("employee_id") : ""
                                ))
                                .extractedAt(rs.getTimestamp("when_changed").toInstant())
                                .build()
                ),

                // 4. Read from Trading Platform
                new JdbcSourceAdapter("TRADE", dataSource,
                        "SELECT trader_cd, trdr_nm, eml, mobile, dob, id_proof_num, desk, book, last_updt_dt FROM trade_db.traders",
                        (rs, source) -> RawIdentityRow.builder()
                                .sourceSystem(source)
                                .externalId(rs.getString("trader_cd"))
                                .fullName(rs.getString("trdr_nm"))
                                .email(rs.getString("eml"))
                                .phone(rs.getString("mobile"))
                                .dateOfBirth(rs.getObject("dob", LocalDate.class))
                                .nationalId(rs.getString("id_proof_num"))
                                .attributes(Map.of(
                                        "desk", rs.getString("desk") != null ? rs.getString("desk") : "",
                                        "book", rs.getString("book") != null ? rs.getString("book") : ""
                                ))
                                .extractedAt(rs.getTimestamp("last_updt_dt").toInstant())
                                .build()
                ),

                // 5. Read from Loan Origination System
                new JdbcSourceAdapter("LOS", dataSource,
                        "SELECT officer_code, officer_full_name, official_email_id, mobile_no, date_of_birth, permanent_account_no, region_code, product_specialty, record_updated_on FROM los_db.loan_officer",
                        (rs, source) -> RawIdentityRow.builder()
                                .sourceSystem(source)
                                .externalId(rs.getString("officer_code"))
                                .fullName(rs.getString("officer_full_name"))
                                .email(rs.getString("official_email_id"))
                                .phone(rs.getString("mobile_no"))
                                .dateOfBirth(rs.getObject("date_of_birth", LocalDate.class))
                                .nationalId(rs.getString("permanent_account_no"))
                                .attributes(Map.of(
                                        "region", rs.getString("region_code") != null ? rs.getString("region_code") : "",
                                        "specialty", rs.getString("product_specialty") != null ? rs.getString("product_specialty") : ""
                                ))
                                .extractedAt(rs.getTimestamp("record_updated_on").toInstant())
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
                // Merge identity fields into the attributes map so the
                // frontend can see *what* drove the match (email, PAN, etc.)
                Map<String, String> enrichedData = new LinkedHashMap<>(subRow.attributes());
                subRow.email().ifPresent(v       -> enrichedData.put("email", v));
                subRow.phone().ifPresent(v       -> enrichedData.put("phone", v));
                subRow.dateOfBirth().ifPresent(v -> enrichedData.put("dateOfBirth", v.toString()));
                subRow.nationalId().ifPresent(v  -> enrichedData.put("nationalId", v));
                enrichedData.put("externalId", subRow.externalId());

                reactNodes.add(new NodeDTO(subRow.id().toString(), subRow.fullName().orElse("Unknown") + " (" + subRow.sourceSystem() + ")", "raw_source", enrichedData));
            }
        }
        for (IdentityEdge edge : rawGraph.edges()) {
            String finalLabel = edge.label();

            if (edge instanceof IdentityEdge.Aggregates agg && !agg.matchedOn().isEmpty()) {
                finalLabel += " (" + String.join(", ", agg.matchedOn()) + ")";
            } else if (edge instanceof IdentityEdge.CrossCorrelation cc && !cc.matchedOn().isEmpty()) {
                finalLabel += " (" + String.join(", ", cc.matchedOn()) + ")";
            }

            reactEdges.add(new EdgeDTO(edge.from().toString(), edge.to().toString(), finalLabel));
        }

        return new GraphResponseDTO(reactNodes, reactEdges);
    }
}

// --- Data Transfer Objects (DTOs) ---
// These records shape the final JSON output.
record NodeDTO(String id, String label, String group, Object data) {}
record EdgeDTO(String source, String target, String label) {}
record GraphResponseDTO(List<NodeDTO> nodes, List<EdgeDTO> edges) {}
