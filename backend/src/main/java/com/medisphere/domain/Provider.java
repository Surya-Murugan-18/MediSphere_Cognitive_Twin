package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Clinical provider (clinician, nurse, analyst, admin) account document.
 * Stored in MongoDB collection: providers
 *
 * SECURITY NOTE: passwordHash stores bcrypt hash only.
 * Plain-text passwords are never stored or logged.
 */
@Document(collection = "providers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Provider {

    /**
     * System-assigned provider ID: PROV-001, PROV-002, ...
     */
    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String email;

    /**
     * BCrypt hashed password — never log or serialize this field.
     */
    private String passwordHash;

    @Builder.Default
    private ProviderRole role = ProviderRole.CLINICIAN;

    private String specialty;

    private String facility;

    /** National Provider Identifier (optional) */
    private String npi;

    @Builder.Default
    private NotificationPrefs notificationPrefs = NotificationPrefs.defaults();

    private Instant lastSignIn;

    @Builder.Default
    private boolean active = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
