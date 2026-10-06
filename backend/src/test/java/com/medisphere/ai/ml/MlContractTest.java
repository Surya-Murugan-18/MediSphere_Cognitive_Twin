package com.medisphere.ai.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medisphere.ai.ml.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * E. Contract tests — verifies that Spring request DTOs match FastAPI schemas
 * and that FastAPI response JSON deserializes correctly into Spring response DTOs.
 *
 * These tests use Jackson directly (no HTTP) to verify the JSON field name
 * contract between the Java records (@JsonProperty) and the FastAPI Pydantic models.
 *
 * FastAPI schema reference:
 *   ml-service/src/api/schemas/cvd_schema.py
 *   ml-service/src/api/schemas/diabetes_schema.py
 *   ml-service/src/api/schemas/readmission_schema.py
 */
@DisplayName("ML DTO Contract Tests — Java ↔ FastAPI JSON schema parity")
class MlContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ── CVD request serialization ─────────────────────────────────────────

    @Test
    @DisplayName("CvdRiskRequest serializes to snake_case matching FastAPI CVDRiskRequest")
    void cvdRequest_serializesToSnakeCase() throws Exception {
        CvdRiskRequest req = new CvdRiskRequest(
                "P001", 1, 55, 135.0, 85.0, 72, 210.0, 95.0, 1, 0, 1, 0);

        String json = mapper.writeValueAsString(req);

        // Verify all field names are snake_case as expected by FastAPI
        assertThat(json).contains("\"patient_id\":\"P001\"");
        assertThat(json).contains("\"male\":1");
        assertThat(json).contains("\"age\":55");
        assertThat(json).contains("\"sys_bp\":135.0");
        assertThat(json).contains("\"dia_bp\":85.0");
        assertThat(json).contains("\"heart_rate\":72");
        assertThat(json).contains("\"tot_chol\":210.0");
        assertThat(json).contains("\"glucose\":95.0");
        assertThat(json).contains("\"prevalent_hyp\":1");
        assertThat(json).contains("\"prevalent_stroke\":0");
        assertThat(json).contains("\"diabetes\":1");
        assertThat(json).contains("\"bp_meds\":0");

        // Ensure no camelCase leak
        assertThat(json).doesNotContain("patientId");
        assertThat(json).doesNotContain("sysBp");
        assertThat(json).doesNotContain("heartRate");
        assertThat(json).doesNotContain("totChol");
    }

    @Test
    @DisplayName("CvdRiskRequest serializes null fields as JSON null (not omitted)")
    void cvdRequest_nullFieldsSerializedAsNull() throws Exception {
        CvdRiskRequest req = new CvdRiskRequest("P001", null, null, null, null, null, null, null, null, null, null, null);
        String json = mapper.writeValueAsString(req);

        assertThat(json).contains("\"sys_bp\":null");
        assertThat(json).contains("\"glucose\":null");
        assertThat(json).contains("\"heart_rate\":null");
    }

    // ── CVD response deserialization ──────────────────────────────────────

    @Test
    @DisplayName("CvdRiskResponse deserializes FastAPI JSON correctly")
    void cvdResponse_deserializesFromFastApiJson() throws Exception {
        String json = """
                {
                  "patient_id": "P001",
                  "model_id": "CVD-10Y",
                  "model_version": "1.0.0",
                  "probability_score": 0.243217,
                  "risk_category": "High",
                  "prediction_binary": 1,
                  "threshold_used": 0.2157,
                  "shap_factors": [
                    {"feature": "age", "value": "55", "contribution": 0.085, "direction": "increases"},
                    {"feature": "sysBP", "value": "135.0", "contribution": 0.042, "direction": "increases"}
                  ],
                  "imputed_fields": ["glucose"],
                  "inference_timestamp": "2026-10-06T10:00:00Z"
                }
                """;

        CvdRiskResponse resp = mapper.readValue(json, CvdRiskResponse.class);

        assertThat(resp.patientId()).isEqualTo("P001");
        assertThat(resp.modelId()).isEqualTo("CVD-10Y");
        assertThat(resp.modelVersion()).isEqualTo("1.0.0");
        assertThat(resp.probabilityScore()).isEqualTo(0.243217);
        assertThat(resp.riskCategory()).isEqualTo("High");
        assertThat(resp.predictionBinary()).isEqualTo(1);
        assertThat(resp.thresholdUsed()).isEqualTo(0.2157);
        assertThat(resp.shapFactors()).hasSize(2);
        assertThat(resp.shapFactors().get(0).feature()).isEqualTo("age");
        assertThat(resp.shapFactors().get(0).contribution()).isEqualTo(0.085);
        assertThat(resp.shapFactors().get(0).direction()).isEqualTo("increases");
        assertThat(resp.imputedFields()).containsExactly("glucose");
        assertThat(resp.inferenceTimestamp()).isEqualTo("2026-10-06T10:00:00Z");
    }

    // ── Diabetes request ──────────────────────────────────────────────────

    @Test
    @DisplayName("DiabetesRiskRequest serializes to snake_case")
    void diabetesRequest_serializesToSnakeCase() throws Exception {
        DiabetesRiskRequest req = new DiabetesRiskRequest("P002", 55, 1, 1, 1, 1, 0, 0, 3);
        String json = mapper.writeValueAsString(req);

        assertThat(json).contains("\"patient_id\":\"P002\"");
        assertThat(json).contains("\"age_years\":55");
        assertThat(json).contains("\"sex_male\":1");
        assertThat(json).contains("\"high_bp\":1");
        assertThat(json).contains("\"high_chol\":1");
        assertThat(json).contains("\"chol_check\":1");
        assertThat(json).contains("\"stroke\":0");
        assertThat(json).contains("\"heart_disease_or_attack\":0");
        assertThat(json).contains("\"phys_hlth_alert_count_30d\":3");
    }

    @Test
    @DisplayName("DiabetesRiskResponse deserializes FastAPI JSON correctly")
    void diabetesResponse_deserializesFromFastApiJson() throws Exception {
        String json = """
                {
                  "patient_id": "P002",
                  "model_id": "diabetes-risk",
                  "model_version": "1.0.0",
                  "probability_score": 0.312,
                  "risk_category": "Low",
                  "prediction_binary": 0,
                  "threshold_used": 0.40,
                  "shap_factors": [
                    {"feature": "HighBP", "value": "1.0", "contribution": 0.06, "direction": "increases"}
                  ],
                  "imputed_fields": [],
                  "inference_timestamp": "2026-10-06T10:00:00Z"
                }
                """;

        DiabetesRiskResponse resp = mapper.readValue(json, DiabetesRiskResponse.class);

        assertThat(resp.patientId()).isEqualTo("P002");
        assertThat(resp.modelId()).isEqualTo("diabetes-risk");
        assertThat(resp.probabilityScore()).isEqualTo(0.312);
        assertThat(resp.predictionBinary()).isEqualTo(0);
        assertThat(resp.shapFactors()).hasSize(1);
        assertThat(resp.imputedFields()).isEmpty();
    }

    // ── Readmission request ───────────────────────────────────────────────

    @Test
    @DisplayName("ReadmissionRequest serializes to snake_case")
    void readmissionRequest_serializesToSnakeCase() throws Exception {
        ReadmissionRequest req = new ReadmissionRequest(
                "P003", 1, 62, 3,
                "Circulatory", "Metabolic/Endocrine", "Unknown",
                2, 1, 0);
        String json = mapper.writeValueAsString(req);

        assertThat(json).contains("\"patient_id\":\"P003\"");
        assertThat(json).contains("\"gender_male\":1");
        assertThat(json).contains("\"age_years\":62");
        assertThat(json).contains("\"number_diagnoses\":3");
        assertThat(json).contains("\"diag_group_primary\":\"Circulatory\"");
        assertThat(json).contains("\"diag_group_secondary\":\"Metabolic/Endocrine\"");
        assertThat(json).contains("\"diag_group_tertiary\":\"Unknown\"");
        assertThat(json).contains("\"number_high_alerts_prior_year\":2");
        assertThat(json).contains("\"diabetes_med\":1");
        assertThat(json).contains("\"care_plan_changed_30d\":0");
    }

    @Test
    @DisplayName("ReadmissionResponse deserializes FastAPI JSON including prediction_time_assumption")
    void readmissionResponse_deserializesFromFastApiJson() throws Exception {
        String json = """
                {
                  "patient_id": "P003",
                  "model_id": "readmission-30d",
                  "model_version": "1.0.0",
                  "probability_score": 0.187,
                  "risk_category": "Medium",
                  "prediction_binary": 0,
                  "threshold_used": 0.30,
                  "shap_factors": [
                    {"feature": "age_midpoint", "value": "65.0", "contribution": 0.04, "direction": "increases"}
                  ],
                  "imputed_fields": ["number_diagnoses"],
                  "prediction_time_assumption": "early_hospitalization",
                  "inference_timestamp": "2026-10-06T10:00:00Z"
                }
                """;

        ReadmissionResponse resp = mapper.readValue(json, ReadmissionResponse.class);

        assertThat(resp.patientId()).isEqualTo("P003");
        assertThat(resp.modelId()).isEqualTo("readmission-30d");
        assertThat(resp.probabilityScore()).isEqualTo(0.187);
        assertThat(resp.predictionTimeAssumption()).isEqualTo("early_hospitalization");
        assertThat(resp.imputedFields()).containsExactly("number_diagnoses");
        assertThat(resp.shapFactors()).hasSize(1);
        assertThat(resp.shapFactors().get(0).feature()).isEqualTo("age_midpoint");
    }

    // ── ShapFactor deserialization ────────────────────────────────────────

    @Test
    @DisplayName("MlShapFactor deserializes all 4 fields correctly")
    void shapFactor_deserializesAllFields() throws Exception {
        String json = """
                {"feature":"glucose","value":"95.0","contribution":-0.031,"direction":"decreases"}
                """;

        MlShapFactor factor = mapper.readValue(json, MlShapFactor.class);

        assertThat(factor.feature()).isEqualTo("glucose");
        assertThat(factor.value()).isEqualTo("95.0");
        assertThat(factor.contribution()).isEqualTo(-0.031);
        assertThat(factor.direction()).isEqualTo("decreases");
    }
}
