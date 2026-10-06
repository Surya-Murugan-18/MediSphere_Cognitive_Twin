package com.medisphere.ai.ml;

import java.util.List;

/**
 * Java port of ml-service/training/utils/condition_mapper.py.
 *
 * Maps free-text Patient.conditions strings to ICD chapter group names
 * for the Readmission-30D model's diag_group_primary/secondary/tertiary features.
 *
 * Also provides proxy-extraction helpers for:
 *   bp_meds      (CVD model)   — CarePlanRecommendation.intervention keyword match
 *   diabetes_med (Readmission) — condition + CarePlanRecommendation.intervention keyword match
 *
 * The mapping algorithm is a direct, deterministic port of the Python implementation.
 * The Java output MUST match the Python output for the same input strings.
 * Do NOT invent a different algorithm.
 *
 * Per ml-service/training/utils/condition_mapper.py — the authoritative source.
 */
public final class ConditionMapper {

    private ConditionMapper() {}

    // ── Group names (must match the Python _ALL_GROUPS list exactly) ──────

    public static final String GROUP_CIRCULATORY      = "Circulatory";
    public static final String GROUP_METABOLIC        = "Metabolic/Endocrine";
    public static final String GROUP_RESPIRATORY      = "Respiratory";
    public static final String GROUP_DIGESTIVE        = "Digestive";
    public static final String GROUP_GENITOURINARY    = "Genitourinary";
    public static final String GROUP_MUSCULOSKELETAL  = "Musculoskeletal";
    public static final String GROUP_MENTAL           = "Mental";
    public static final String GROUP_NEOPLASMS        = "Neoplasms";
    public static final String GROUP_NERVOUS          = "Nervous";
    public static final String GROUP_UNKNOWN          = "Unknown";

    // ── Keyword → group mapping (exact port of _KEYWORD_MAP in Python) ────

    private static final String[][] KEYWORD_GROUPS = {
        // Circulatory
        {
            "heart", "cardiac", "coronary", "cad", "chd", "myocardial",
            "hypertension", "arrhythmia", "atrial", "stroke", "vascular",
            "infarction", "angina", "ischemic heart",
            GROUP_CIRCULATORY
        },
        // Metabolic/Endocrine
        {
            "diabetes", "thyroid", "obesity", "hyperlipidemia", "cholesterol",
            "metabolic", "endocrine", "gout", "hypercholesterolemia",
            GROUP_METABOLIC
        },
        // Respiratory
        {
            "asthma", "copd", "pneumonia", "respiratory", "lung",
            "pulmonary", "bronchitis", "emphysema",
            GROUP_RESPIRATORY
        },
        // Digestive
        {
            "gastric", "hepatic", "liver", "cirrhosis", "pancreatitis",
            "bowel", "crohn", "ulcer", "digestive", "colitis",
            GROUP_DIGESTIVE
        },
        // Genitourinary
        {
            "kidney", "renal", "nephropathy", "dialysis",
            GROUP_GENITOURINARY
        },
        // Musculoskeletal
        {
            "arthritis", "osteoporosis", "fracture", "joint",
            "spine", "musculoskeletal", "fibromyalgia",
            GROUP_MUSCULOSKELETAL
        },
        // Mental
        {
            "depression", "anxiety", "psychiatric", "mental",
            "dementia", "alzheimer", "schizophrenia",
            GROUP_MENTAL
        },
        // Neoplasms
        {
            "cancer", "tumor", "malignant", "neoplasm",
            "lymphoma", "leukemia", "carcinoma",
            GROUP_NEOPLASMS
        },
        // Nervous
        {
            "neuropathy", "parkinson", "epilepsy", "seizure",
            "nervous", "neurological", "multiple sclerosis",
            GROUP_NERVOUS
        },
    };

    // ── Antihypertensive keywords for bp_meds proxy (CVD) ─────────────────
    // Port of condition_mapper.py BP_MED_KEYWORDS
    private static final String[] BP_MED_KEYWORDS = {
        "lisinopril", "amlodipine", "metoprolol", "atenolol",
        "antihypertensive", "blood pressure medication", "bp medication",
        "losartan", "valsartan", "hydrochlorothiazide", "ramipril",
        "enalapril", "carvedilol", "bisoprolol", "nifedipine",
    };

    // ── Diabetes medication keywords for diabetesMed proxy (Readmission) ──
    // Port of condition_mapper.py DIABETES_MED_KEYWORDS
    private static final String[] DIABETES_MED_KEYWORDS = {
        "metformin", "insulin", "glipizide", "glimepiride", "pioglitazone",
        "diabetes medication", "antidiabetic", "glyburide", "repaglinide",
        "sitagliptin", "liraglutide", "empagliflozin", "dapagliflozin",
    };

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Map a single free-text condition name to a group.
     * Port of condition_mapper.py::condition_name_to_group().
     *
     * @param condition free-text condition (e.g. "Type 2 Diabetes")
     * @return one of the GROUP_* constants, "Unknown" if no match
     */
    public static String conditionNameToGroup(String condition) {
        if (condition == null || condition.isBlank()) {
            return GROUP_UNKNOWN;
        }
        String lower = condition.toLowerCase();

        for (String[] row : KEYWORD_GROUPS) {
            // Last element is the group name; all preceding elements are keywords
            String groupName = row[row.length - 1];
            for (int i = 0; i < row.length - 1; i++) {
                if (lower.contains(row[i])) {
                    return groupName;
                }
            }
        }
        return GROUP_UNKNOWN;
    }

    /**
     * Convert a Patient.conditions list to (diag_group_primary, diag_group_secondary, diag_group_tertiary).
     * Port of condition_mapper.py::conditions_list_to_diag_groups().
     *
     * @param conditions list of free-text condition strings
     * @return array of exactly 3 group strings [primary, secondary, tertiary]
     */
    public static String[] conditionsToDigGroups(List<String> conditions) {
        String g1 = (conditions != null && conditions.size() > 0)
                ? conditionNameToGroup(conditions.get(0)) : GROUP_UNKNOWN;
        String g2 = (conditions != null && conditions.size() > 1)
                ? conditionNameToGroup(conditions.get(1)) : GROUP_UNKNOWN;
        String g3 = (conditions != null && conditions.size() > 2)
                ? conditionNameToGroup(conditions.get(2)) : GROUP_UNKNOWN;
        return new String[]{g1, g2, g3};
    }

    /**
     * CVD bp_meds proxy: 1 if any CarePlanRecommendation.intervention contains
     * antihypertensive keywords.
     * Port of condition_mapper.py::extract_bp_meds().
     *
     * @param interventions list of CarePlanRecommendation.intervention strings
     * @param titles        list of CarePlanRecommendation.title strings (same order)
     * @return 1 if BP medication keyword found, 0 otherwise
     */
    public static int extractBpMeds(List<String> interventions, List<String> titles) {
        if (interventions == null && titles == null) return 0;

        int size = interventions != null ? interventions.size()
                 : (titles != null ? titles.size() : 0);

        for (int i = 0; i < size; i++) {
            String intervention = (interventions != null && i < interventions.size())
                    ? interventions.get(i) : "";
            String title = (titles != null && i < titles.size())
                    ? titles.get(i) : "";
            if (hasKeyword(intervention, BP_MED_KEYWORDS)
                    || hasKeyword(title, BP_MED_KEYWORDS)) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * Readmission diabetesMed proxy.
     * Port of condition_mapper.py::extract_diabetes_med().
     *
     * Logic:
     *   - If patient has no diabetes condition → 0
     *   - If diabetic AND care plan has diabetes medication keyword → 1
     *   - If diabetic AND no care plan → 1 (assume on medication)
     *   - If diabetic with care plan but no diabetes med keyword → 1 (assume yes)
     *
     * @param conditions    Patient.conditions list
     * @param interventions CarePlanRecommendation.intervention strings
     * @param titles        CarePlanRecommendation.title strings (same order)
     * @return 0 or 1
     */
    public static int extractDiabetesMed(List<String> conditions,
                                          List<String> interventions,
                                          List<String> titles) {
        boolean hasDiabetes = conditions != null && conditions.stream()
                .anyMatch(c -> c != null && c.toLowerCase().contains("diabetes"));

        if (!hasDiabetes) return 0;

        // Diabetic patient — check care plan for explicit medication reference
        if ((interventions == null || interventions.isEmpty())
                && (titles == null || titles.isEmpty())) {
            return 1; // diabetic, no care plan → assume on medication
        }

        int size = interventions != null ? interventions.size()
                 : (titles != null ? titles.size() : 0);

        for (int i = 0; i < size; i++) {
            String intervention = (interventions != null && i < interventions.size())
                    ? interventions.get(i) : "";
            String title = (titles != null && i < titles.size())
                    ? titles.get(i) : "";
            if (hasKeyword(intervention, DIABETES_MED_KEYWORDS)
                    || hasKeyword(title, DIABETES_MED_KEYWORDS)) {
                return 1;
            }
        }
        // Diabetic with care plan but no diabetes med keyword → still assume yes
        return 1;
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Case-insensitive keyword search.
     * Port of condition_mapper.py::has_keyword().
     */
    private static boolean hasKeyword(String text, String[] keywords) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }
}
