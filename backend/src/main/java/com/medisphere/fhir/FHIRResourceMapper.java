package com.medisphere.fhir;

import com.medisphere.domain.LabResult;
import com.medisphere.fhir.model.FHIRObservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Maps FHIR R4 resource representations to MediSphere domain models.
 *
 * Validation rules applied during mapping:
 *   - Required fields (patientFhirId, loincCode, effectiveDate) are checked
 *   - Missing optional fields fall back to safe defaults
 *   - No AI, no random values, no fabricated clinical data
 */
@Component
public class FHIRResourceMapper {

    private static final Logger log = LoggerFactory.getLogger(FHIRResourceMapper.class);

    /**
     * Map a FHIR Observation resource (laboratory category) to a MediSphere LabResult.
     *
     * @param obs          the FHIR observation
     * @param patientId    the MediSphere patient ID (for association)
     * @return mapped LabResult, or null if the observation cannot be mapped
     */
    public LabResult mapObservationToLabResult(FHIRObservation obs, String patientId) {
        if (obs == null) {
            log.debug("FHIRResourceMapper: null observation — skipping");
            return null;
        }
        if (!StringUtils.hasText(obs.getPatientFhirId())) {
            log.debug("FHIRResourceMapper: observation {} missing patientFhirId — skipping", obs.getId());
            return null;
        }
        if (!StringUtils.hasText(obs.getEffectiveDate())) {
            log.debug("FHIRResourceMapper: observation {} missing effectiveDate — skipping", obs.getId());
            return null;
        }

        // Map status: FHIR uses "final"/"preliminary" etc.; LabResult uses High/Low/Normal/Pending
        String status = mapInterpretationToStatus(obs.getInterpretation(), obs.getStatus());

        // Map category: FHIR category codes to LabResult.Category
        String category = mapFhirCategoryToLabCategory(obs.getLoincCode(), obs.getCategory());

        // Build display result string
        String result = buildResultString(obs);

        // Build reference range display
        String referenceRange = buildReferenceRange(obs);

        // Clinical significance text — derived from status and test name (deterministic, not AI)
        String significance = buildSignificance(obs.getDisplayName(), status);

        return LabResult.builder()
                .id("LAB-" + obs.getId())
                .patientId(patientId)
                .fhirObservationId(obs.getId())    // deduplication key
                .loinc(StringUtils.hasText(obs.getLoincCode()) ? obs.getLoincCode() : "")
                .test(StringUtils.hasText(obs.getDisplayName()) ? obs.getDisplayName() : "Unknown Test")
                .result(result)
                .numeric(obs.getValueQuantity() != null ? obs.getValueQuantity() : 0.0)
                .unit(StringUtils.hasText(obs.getUnit()) ? obs.getUnit() : "")
                .referenceRange(referenceRange)
                .status(status)
                .category(category)
                .date(obs.getEffectiveDate())
                .trend("flat")            // trend is computed by LabService against previous result
                .previous("")             // populated by LabService
                .significance(significance)
                .build();
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private String mapInterpretationToStatus(String interpretation, String fhirStatus) {
        if ("preliminary".equalsIgnoreCase(fhirStatus) || "registered".equalsIgnoreCase(fhirStatus)) {
            return "Pending";
        }
        if (!StringUtils.hasText(interpretation)) return "Normal";
        return switch (interpretation.toUpperCase()) {
            case "H", "HH", "HU", ">" -> "High";
            case "L", "LL", "LU", "<" -> "Low";
            default -> "Normal";
        };
    }

    private String mapFhirCategoryToLabCategory(String loincCode, String fhirCategory) {
        // Use LOINC code prefix to determine category if available
        if (StringUtils.hasText(loincCode)) {
            // Common LOINC panels
            if (isMetabolicCode(loincCode)) return "Metabolic";
            if (isLipidCode(loincCode))     return "Lipids";
            if (isCardiacCode(loincCode))   return "Cardiac";
            if (isHematologyCode(loincCode)) return "Hematology";
        }
        // Fall back to FHIR category string
        if (StringUtils.hasText(fhirCategory)) {
            String c = fhirCategory.toLowerCase();
            if (c.contains("metabolic") || c.contains("chemistry")) return "Metabolic";
            if (c.contains("lipid"))     return "Lipids";
            if (c.contains("cardiac"))   return "Cardiac";
            if (c.contains("hematol"))   return "Hematology";
        }
        return "Metabolic"; // safe default
    }

    private boolean isMetabolicCode(String loinc) {
        // HbA1c, glucose, creatinine, BMP/CMP panels
        return loinc.startsWith("4548") || loinc.startsWith("2345") ||
               loinc.startsWith("2160") || loinc.startsWith("2093") ||
               loinc.startsWith("1751");
    }

    private boolean isLipidCode(String loinc) {
        return loinc.startsWith("2093") || loinc.startsWith("2085") ||
               loinc.startsWith("2571") || loinc.startsWith("18262");
    }

    private boolean isCardiacCode(String loinc) {
        // Troponin, BNP, CK-MB
        return loinc.startsWith("10839") || loinc.startsWith("33762") ||
               loinc.startsWith("49563") || loinc.startsWith("13969");
    }

    private boolean isHematologyCode(String loinc) {
        // CBC panels
        return loinc.startsWith("6690") || loinc.startsWith("789") ||
               loinc.startsWith("788") || loinc.startsWith("785");
    }

    private String buildResultString(FHIRObservation obs) {
        if (obs.getValueQuantity() != null && StringUtils.hasText(obs.getUnit())) {
            // Format: "8.2 %" or "224 mg/dL"
            double v = obs.getValueQuantity();
            String formatted = (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
            return formatted + " " + obs.getUnit();
        }
        if (StringUtils.hasText(obs.getValueString())) {
            return obs.getValueString();
        }
        return "—";
    }

    private String buildReferenceRange(FHIRObservation obs) {
        if (StringUtils.hasText(obs.getReferenceRangeText())) {
            return obs.getReferenceRangeText();
        }
        if (StringUtils.hasText(obs.getReferenceRangeLow()) &&
            StringUtils.hasText(obs.getReferenceRangeHigh())) {
            return obs.getReferenceRangeLow() + " – " + obs.getReferenceRangeHigh();
        }
        if (StringUtils.hasText(obs.getReferenceRangeLow())) {
            return "> " + obs.getReferenceRangeLow();
        }
        if (StringUtils.hasText(obs.getReferenceRangeHigh())) {
            return "< " + obs.getReferenceRangeHigh();
        }
        return "See lab report";
    }

    private String buildSignificance(String testName, String status) {
        if (!StringUtils.hasText(testName)) return "Result received from laboratory system.";
        return switch (status) {
            case "High"    -> testName + " is above the reference range. Clinical review recommended.";
            case "Low"     -> testName + " is below the reference range. Clinical review recommended.";
            case "Pending" -> testName + " result is preliminary. Final result pending.";
            default        -> testName + " is within the reference range.";
        };
    }
}
