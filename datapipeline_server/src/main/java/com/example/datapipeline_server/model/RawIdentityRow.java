package com.example.datapipeline_server.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * The canonical, source-agnostic row produced by every {@code SourceAdapter}
 * after extraction. This is the lingua franca that the correlation pipeline
 * consumes — adapters are responsible for translating their native schema
 * (Core Banking, AD/LDAP, Email directory, Trading desk, etc.) into this form.
 * <p>
 * Optional fields express "this source system does not carry this attribute"
 * rather than "the value is null". Use {@code Optional.empty()} for missing
 * columns; never pass {@code null}.
 */
public record RawIdentityRow(
        UUID id,
        String sourceSystem,
        String externalId,
        Optional<String> fullName,
        Optional<String> email,
        Optional<String> phone,
        Optional<LocalDate> dateOfBirth,
        Optional<String> nationalId,
        Map<String, String> attributes,
        Instant extractedAt
) {
    public RawIdentityRow {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceSystem, "sourceSystem");
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(extractedAt, "extractedAt");
        fullName     = fullName     == null ? Optional.empty() : fullName;
        email        = email        == null ? Optional.empty() : email;
        phone        = phone        == null ? Optional.empty() : phone;
        dateOfBirth  = dateOfBirth  == null ? Optional.empty() : dateOfBirth;
        nationalId   = nationalId   == null ? Optional.empty() : nationalId;
        attributes   = attributes   == null ? Map.of()         : Map.copyOf(attributes);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID id = UUID.randomUUID();
        private String sourceSystem;
        private String externalId;
        private Optional<String> fullName = Optional.empty();
        private Optional<String> email = Optional.empty();
        private Optional<String> phone = Optional.empty();
        private Optional<LocalDate> dateOfBirth = Optional.empty();
        private Optional<String> nationalId = Optional.empty();
        private Map<String, String> attributes = Map.of();
        private Instant extractedAt = Instant.now();

        public Builder id(UUID v) { this.id = v; return this; }
        public Builder sourceSystem(String v) { this.sourceSystem = v; return this; }
        public Builder externalId(String v) { this.externalId = v; return this; }
        public Builder fullName(String v) { this.fullName = Optional.ofNullable(v); return this; }
        public Builder email(String v) { this.email = Optional.ofNullable(v); return this; }
        public Builder phone(String v) { this.phone = Optional.ofNullable(v); return this; }
        public Builder dateOfBirth(LocalDate v) { this.dateOfBirth = Optional.ofNullable(v); return this; }
        public Builder nationalId(String v) { this.nationalId = Optional.ofNullable(v); return this; }
        public Builder attributes(Map<String, String> v) { this.attributes = v; return this; }
        public Builder extractedAt(Instant v) { this.extractedAt = v; return this; }

        public RawIdentityRow build() {
            return new RawIdentityRow(id, sourceSystem, externalId, fullName, email,
                    phone, dateOfBirth, nationalId, attributes, extractedAt);
        }
    }
}
