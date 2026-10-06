package com.medisphere.ai.ml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ConditionMapper.
 *
 * Verifies Java output matches the Python condition_mapper.py behaviour
 * for representative condition strings used in MediSphere.
 *
 * Test cases are drawn from:
 *   ml-service/training/utils/condition_mapper.py _KEYWORD_MAP
 */
@DisplayName("ConditionMapper — Java / Python parity tests")
class ConditionMapperTest {

    // ── conditionNameToGroup ──────────────────────────────────────────────

    @ParameterizedTest(name = "''{0}'' → Circulatory")
    @ValueSource(strings = {
        "Hypertension", "Heart failure", "Coronary artery disease",
        "CAD", "Myocardial infarction", "Atrial fibrillation",
        "Stroke", "Vascular disease", "Angina", "Ischemic heart disease",
        "HEART FAILURE",     // case insensitive
        "atrial flutter"     // partial keyword match
    })
    @DisplayName("Circulatory conditions map to Circulatory")
    void circulatory_conditions(String condition) {
        assertThat(ConditionMapper.conditionNameToGroup(condition))
                .isEqualTo(ConditionMapper.GROUP_CIRCULATORY);
    }

    @ParameterizedTest(name = "''{0}'' → Metabolic/Endocrine")
    @ValueSource(strings = {
        "Type 2 Diabetes", "Diabetes mellitus", "Thyroid disorder",
        "Obesity", "Hyperlipidemia", "High cholesterol",
        "Metabolic syndrome", "Endocrine disorder", "Gout",
        "Hypercholesterolemia"
    })
    @DisplayName("Metabolic/Endocrine conditions map correctly")
    void metabolic_conditions(String condition) {
        assertThat(ConditionMapper.conditionNameToGroup(condition))
                .isEqualTo(ConditionMapper.GROUP_METABOLIC);
    }

    @ParameterizedTest(name = "''{0}'' → Respiratory")
    @ValueSource(strings = {
        "Asthma", "COPD", "Pneumonia", "Respiratory failure",
        "Lung disease", "Pulmonary fibrosis", "Bronchitis", "Emphysema"
    })
    @DisplayName("Respiratory conditions map correctly")
    void respiratory_conditions(String condition) {
        assertThat(ConditionMapper.conditionNameToGroup(condition))
                .isEqualTo(ConditionMapper.GROUP_RESPIRATORY);
    }

    @ParameterizedTest(name = "''{0}'' → Genitourinary")
    @ValueSource(strings = {
        "Chronic kidney disease", "Renal failure", "Nephropathy", "Dialysis"
    })
    @DisplayName("Genitourinary conditions map correctly")
    void genitourinary_conditions(String condition) {
        assertThat(ConditionMapper.conditionNameToGroup(condition))
                .isEqualTo(ConditionMapper.GROUP_GENITOURINARY);
    }

    @ParameterizedTest(name = "''{0}'' → Musculoskeletal")
    @ValueSource(strings = {
        "Rheumatoid arthritis", "Osteoporosis", "Hip fracture",
        "Joint pain", "Spine disorder", "Fibromyalgia"
    })
    @DisplayName("Musculoskeletal conditions map correctly")
    void musculoskeletal_conditions(String condition) {
        assertThat(ConditionMapper.conditionNameToGroup(condition))
                .isEqualTo(ConditionMapper.GROUP_MUSCULOSKELETAL);
    }

    @ParameterizedTest(name = "''{0}'' → Mental")
    @ValueSource(strings = {
        "Depression", "Anxiety disorder", "Psychiatric disorder",
        "Mental illness", "Dementia", "Alzheimer disease", "Schizophrenia"
    })
    @DisplayName("Mental conditions map correctly")
    void mental_conditions(String condition) {
        assertThat(ConditionMapper.conditionNameToGroup(condition))
                .isEqualTo(ConditionMapper.GROUP_MENTAL);
    }

    @ParameterizedTest(name = "''{0}'' → Neoplasms")
    @ValueSource(strings = {
        "Breast cancer", "Breast tumor", "Malignant neoplasm",
        "Lymphoma", "Leukemia", "Carcinoma"
    })
    @DisplayName("Neoplasm conditions map correctly")
    void neoplasm_conditions(String condition) {
        assertThat(ConditionMapper.conditionNameToGroup(condition))
                .isEqualTo(ConditionMapper.GROUP_NEOPLASMS);
    }

    @ParameterizedTest(name = "''{0}'' → Nervous")
    @ValueSource(strings = {
        "Peripheral neuropathy", "Parkinson disease", "Epilepsy",
        "Seizure disorder", "Nervous system disorder", "Multiple sclerosis"
    })
    @DisplayName("Nervous conditions map correctly")
    void nervous_conditions(String condition) {
        assertThat(ConditionMapper.conditionNameToGroup(condition))
                .isEqualTo(ConditionMapper.GROUP_NERVOUS);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "Unknown condition XYZ-9999"})
    @DisplayName("Unknown/null/blank/unrecognised conditions → Unknown")
    void unknown_conditions(String condition) {
        assertThat(ConditionMapper.conditionNameToGroup(condition))
                .isEqualTo(ConditionMapper.GROUP_UNKNOWN);
    }

    // ── conditionsToDigGroups ─────────────────────────────────────────────

    @Test
    @DisplayName("conditionsToDigGroups: maps first 3 conditions correctly")
    void conditionsToDigGroups_mapsFirst3() {
        List<String> conditions = List.of(
                "Hypertension",
                "Type 2 Diabetes",
                "Asthma",
                "Some other condition"   // 4th — should be ignored
        );
        String[] groups = ConditionMapper.conditionsToDigGroups(conditions);
        assertThat(groups).hasSize(3);
        assertThat(groups[0]).isEqualTo(ConditionMapper.GROUP_CIRCULATORY);
        assertThat(groups[1]).isEqualTo(ConditionMapper.GROUP_METABOLIC);
        assertThat(groups[2]).isEqualTo(ConditionMapper.GROUP_RESPIRATORY);
    }

    @Test
    @DisplayName("conditionsToDigGroups: null list returns three Unknown")
    void conditionsToDigGroups_nullList() {
        String[] groups = ConditionMapper.conditionsToDigGroups(null);
        assertThat(groups).hasSize(3);
        assertThat(groups).containsOnly(ConditionMapper.GROUP_UNKNOWN);
    }

    @Test
    @DisplayName("conditionsToDigGroups: single condition → primary set, rest Unknown")
    void conditionsToDigGroups_singleCondition() {
        String[] groups = ConditionMapper.conditionsToDigGroups(List.of("Stroke"));
        assertThat(groups[0]).isEqualTo(ConditionMapper.GROUP_CIRCULATORY);
        assertThat(groups[1]).isEqualTo(ConditionMapper.GROUP_UNKNOWN);
        assertThat(groups[2]).isEqualTo(ConditionMapper.GROUP_UNKNOWN);
    }

    @Test
    @DisplayName("conditionsToDigGroups: empty list returns three Unknown")
    void conditionsToDigGroups_emptyList() {
        String[] groups = ConditionMapper.conditionsToDigGroups(List.of());
        assertThat(groups).containsOnly(ConditionMapper.GROUP_UNKNOWN);
    }

    // ── extractBpMeds ─────────────────────────────────────────────────────

    @Test
    @DisplayName("extractBpMeds: returns 1 when intervention contains antihypertensive keyword")
    void extractBpMeds_lisinopril_returns_1() {
        List<String> interventions = List.of("Prescribe lisinopril 10mg daily");
        List<String> titles        = List.of("Antihypertensive therapy");
        assertThat(ConditionMapper.extractBpMeds(interventions, titles)).isEqualTo(1);
    }

    @Test
    @DisplayName("extractBpMeds: returns 1 when title contains 'antihypertensive'")
    void extractBpMeds_title_keyword_returns_1() {
        List<String> interventions = List.of("Take medication as prescribed");
        List<String> titles        = List.of("Antihypertensive medication review");
        assertThat(ConditionMapper.extractBpMeds(interventions, titles)).isEqualTo(1);
    }

    @Test
    @DisplayName("extractBpMeds: returns 0 when no antihypertensive keyword found")
    void extractBpMeds_no_keyword_returns_0() {
        List<String> interventions = List.of("Increase physical activity", "Reduce salt intake");
        List<String> titles        = List.of("Lifestyle modification", "Diet plan");
        assertThat(ConditionMapper.extractBpMeds(interventions, titles)).isEqualTo(0);
    }

    @Test
    @DisplayName("extractBpMeds: returns 0 for null inputs")
    void extractBpMeds_null_inputs_returns_0() {
        assertThat(ConditionMapper.extractBpMeds(null, null)).isEqualTo(0);
    }

    @Test
    @DisplayName("extractBpMeds: amlodipine in intervention → 1")
    void extractBpMeds_amlodipine_returns_1() {
        assertThat(ConditionMapper.extractBpMeds(
                List.of("Amlodipine 5mg once daily"), List.of())).isEqualTo(1);
    }

    // ── extractDiabetesMed ────────────────────────────────────────────────

    @Test
    @DisplayName("extractDiabetesMed: diabetic + metformin intervention → 1")
    void extractDiabetesMed_diabetic_with_metformin_returns_1() {
        assertThat(ConditionMapper.extractDiabetesMed(
                List.of("Type 2 Diabetes"),
                List.of("Prescribe metformin 500mg twice daily"),
                List.of("Diabetes medication management"))).isEqualTo(1);
    }

    @Test
    @DisplayName("extractDiabetesMed: non-diabetic patient → 0 regardless of intervention")
    void extractDiabetesMed_non_diabetic_returns_0() {
        assertThat(ConditionMapper.extractDiabetesMed(
                List.of("Hypertension"),
                List.of("Prescribe metformin"),
                List.of())).isEqualTo(0);
    }

    @Test
    @DisplayName("extractDiabetesMed: diabetic + no care plan → 1 (assume on medication)")
    void extractDiabetesMed_diabetic_no_careplan_returns_1() {
        assertThat(ConditionMapper.extractDiabetesMed(
                List.of("Diabetes mellitus"),
                null,
                null)).isEqualTo(1);
    }

    @Test
    @DisplayName("extractDiabetesMed: diabetic + care plan without med keyword → 1 (assume yes)")
    void extractDiabetesMed_diabetic_careplan_no_keyword_returns_1() {
        assertThat(ConditionMapper.extractDiabetesMed(
                List.of("Type 2 Diabetes"),
                List.of("Regular exercise 30 minutes daily"),
                List.of("Lifestyle modification"))).isEqualTo(1);
    }

    @Test
    @DisplayName("extractDiabetesMed: null conditions → 0")
    void extractDiabetesMed_null_conditions_returns_0() {
        assertThat(ConditionMapper.extractDiabetesMed(null, List.of(), List.of())).isEqualTo(0);
    }

    @Test
    @DisplayName("extractDiabetesMed: diabetic + insulin keyword → 1")
    void extractDiabetesMed_insulin_returns_1() {
        assertThat(ConditionMapper.extractDiabetesMed(
                List.of("Diabetes type 1"),
                List.of("Insulin glargine 10 units at bedtime"),
                List.of())).isEqualTo(1);
    }

    // ── Determinism ───────────────────────────────────────────────────────

    @Test
    @DisplayName("conditionNameToGroup: same input always produces same output")
    void conditionNameToGroup_isDeterministic() {
        String condition = "Type 2 Diabetes Mellitus";
        String result1 = ConditionMapper.conditionNameToGroup(condition);
        String result2 = ConditionMapper.conditionNameToGroup(condition);
        assertThat(result1).isEqualTo(result2);
        assertThat(result1).isEqualTo(ConditionMapper.GROUP_METABOLIC);
    }
}
