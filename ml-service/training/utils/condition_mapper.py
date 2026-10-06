"""
ICD chapter group mapper — used by Readmission-30D feature engineering.
Maps free-text condition names (from Patient.conditions) OR ICD-9 codes
(from the UCI dataset diag_1/2/3 columns) to 9 clinical chapter groups.

Also handles diabetesMed and change proxy feature extraction for inference.
"""
from __future__ import annotations

import re

# ── ICD-9 numeric range → group ───────────────────────────────────────────────
_ICD_RANGES: list[tuple[float, float, str]] = [
    (1,    139,  "Infectious"),
    (140,  239,  "Neoplasms"),
    (240,  279,  "Metabolic/Endocrine"),
    (280,  289,  "Blood"),
    (290,  319,  "Mental"),
    (320,  389,  "Nervous"),
    (390,  459,  "Circulatory"),
    (460,  519,  "Respiratory"),
    (520,  579,  "Digestive"),
    (580,  629,  "Genitourinary"),
    (630,  679,  "Pregnancy"),
    (680,  709,  "Skin"),
    (710,  739,  "Musculoskeletal"),
    (740,  759,  "Congenital"),
    (800,  999,  "Injury"),
]

# ── Condition name keyword → group (used for MediSphere inference) ────────────
_KEYWORD_MAP: list[tuple[list[str], str]] = [
    (["heart", "cardiac", "coronary", "cad", "chd", "myocardial",
      "hypertension", "arrhythmia", "atrial", "stroke", "vascular",
      "infarction", "angina", "ischemic heart"],        "Circulatory"),
    (["diabetes", "thyroid", "obesity", "hyperlipidemia", "cholesterol",
      "metabolic", "endocrine", "gout", "hypercholesterolemia"],
                                                         "Metabolic/Endocrine"),
    (["asthma", "copd", "pneumonia", "respiratory", "lung",
      "pulmonary", "bronchitis", "emphysema"],           "Respiratory"),
    (["gastric", "hepatic", "liver", "cirrhosis", "pancreatitis",
      "bowel", "crohn", "ulcer", "digestive", "colitis"], "Digestive"),
    (["kidney", "renal", "nephropathy", "dialysis"],     "Genitourinary"),
    (["arthritis", "osteoporosis", "fracture", "joint",
      "spine", "musculoskeletal", "fibromyalgia"],       "Musculoskeletal"),
    (["depression", "anxiety", "psychiatric", "mental",
      "dementia", "alzheimer", "schizophrenia"],         "Mental"),
    (["cancer", "tumor", "malignant", "neoplasm",
      "lymphoma", "leukemia", "carcinoma"],              "Neoplasms"),
    (["neuropathy", "parkinson", "epilepsy", "seizure",
      "nervous", "neurological", "multiple sclerosis"],  "Nervous"),
]

_ALL_GROUPS = [
    "Circulatory", "Metabolic/Endocrine", "Respiratory", "Digestive",
    "Genitourinary", "Musculoskeletal", "Mental", "Neoplasms", "Nervous", "Unknown"
]

# ── antihypertensive keywords for BPMeds proxy (CVD) ─────────────────────────
BP_MED_KEYWORDS = [
    "lisinopril", "amlodipine", "metoprolol", "atenolol",
    "antihypertensive", "blood pressure medication", "bp medication",
    "losartan", "valsartan", "hydrochlorothiazide", "ramipril",
    "enalapril", "carvedilol", "bisoprolol", "nifedipine",
]

# ── diabetes medication keywords for diabetesMed proxy (Readmission) ─────────
DIABETES_MED_KEYWORDS = [
    "metformin", "insulin", "glipizide", "glimepiride", "pioglitazone",
    "diabetes medication", "antidiabetic", "glyburide", "repaglinide",
    "sitagliptin", "liraglutide", "empagliflozin", "dapagliflozin",
]


def icd_code_to_group(code: str | None) -> str:
    """
    Map a raw ICD-9 code string from the UCI dataset to a group name.
    Handles numeric codes, V-codes, and E-codes.
    """
    if code is None or str(code).strip() in ("", "nan", "NaN"):
        return "Unknown"
    code = str(code).strip()
    if code.upper().startswith("V") or code.upper().startswith("E"):
        return "External"
    try:
        num = float(re.sub(r"[^0-9.]", "", code))
        for lo, hi, group in _ICD_RANGES:
            if lo <= num <= hi:
                return group
        return "Unknown"
    except (ValueError, TypeError):
        return "Unknown"


def condition_name_to_group(condition: str | None) -> str:
    """
    Map a free-text condition name (from Patient.conditions in MediSphere) to a group.
    Used during inference.
    """
    if condition is None or str(condition).strip() == "":
        return "Unknown"
    cond_lower = str(condition).lower()
    for keywords, group in _KEYWORD_MAP:
        if any(kw in cond_lower for kw in keywords):
            return group
    return "Unknown"


def conditions_list_to_diag_groups(conditions: list[str] | None) -> tuple[str, str, str]:
    """
    Convert a Patient.conditions list to (diag_group_1, diag_group_2, diag_group_3).
    Used by Spring Boot feature assembly at inference time.
    """
    if not conditions:
        return "Unknown", "Unknown", "Unknown"
    g1 = condition_name_to_group(conditions[0]) if len(conditions) > 0 else "Unknown"
    g2 = condition_name_to_group(conditions[1]) if len(conditions) > 1 else "Unknown"
    g3 = condition_name_to_group(conditions[2]) if len(conditions) > 2 else "Unknown"
    return g1, g2, g3


def has_keyword(text: str | None, keywords: list[str]) -> bool:
    """Case-insensitive keyword search in a free-text string."""
    if not text:
        return False
    t = text.lower()
    return any(kw in t for kw in keywords)


def extract_bp_meds(recommendations: list[dict] | None) -> int:
    """
    CVD BPMeds proxy: 1 if any active CarePlan recommendation's
    intervention text contains antihypertensive keywords.
    """
    if not recommendations:
        return 0
    for rec in recommendations:
        intervention = rec.get("intervention", "") or ""
        title = rec.get("title", "") or ""
        if has_keyword(intervention, BP_MED_KEYWORDS) or has_keyword(title, BP_MED_KEYWORDS):
            return 1
    return 0


def extract_diabetes_med(conditions: list[str] | None,
                          recommendations: list[dict] | None) -> int:
    """
    Readmission diabetesMed proxy:
    1 if patient has diabetes condition AND active CarePlan recommends diabetes meds.
    Falls back to 1 if diabetic patient has no care plan (assumed on medication).
    """
    has_diabetes = any(
        "diabetes" in (c or "").lower() for c in (conditions or [])
    )
    if not has_diabetes:
        return 0
    if not recommendations:
        return 1  # diabetic patient, no care plan → assume on medication
    for rec in recommendations:
        intervention = rec.get("intervention", "") or ""
        title = rec.get("title", "") or ""
        if has_keyword(intervention, DIABETES_MED_KEYWORDS) or has_keyword(title, DIABETES_MED_KEYWORDS):
            return 1
    return 1  # diabetic with care plan but no diabetes med keyword → still assume yes


def all_groups() -> list[str]:
    return _ALL_GROUPS.copy()
