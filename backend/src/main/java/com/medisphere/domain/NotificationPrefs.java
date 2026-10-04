package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-provider notification preferences stored as an embedded document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPrefs {

    @Builder.Default
    private boolean critical = true;

    @Builder.Default
    private boolean risk = true;

    @Builder.Default
    private boolean approvals = true;

    @Builder.Default
    private boolean system = false;

    public static NotificationPrefs defaults() {
        return NotificationPrefs.builder().build();
    }
}
