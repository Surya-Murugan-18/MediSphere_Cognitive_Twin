import { z } from 'zod';
import { PageResponseSchema } from './patient.schema';

// ── SHAP factor ───────────────────────────────────────────────────────────

export const ShapFactorSchema = z.object({
  feature:      z.string(),
  contribution: z.number(),
  value:        z.string(),
  direction:    z.enum(['increases', 'decreases']),
});
export type ShapFactor = z.infer<typeof ShapFactorSchema>;

// ── Clinical evidence citation ────────────────────────────────────────────

export const ClinicalEvidenceSchema = z.object({
  guideline: z.string(),
  detail:    z.string(),
});
export type ClinicalEvidence = z.infer<typeof ClinicalEvidenceSchema>;

// ── Single prediction ─────────────────────────────────────────────────────

export const PredictionSchema = z.object({
  id:               z.string(),
  patientId:        z.string(),
  model:            z.string(),
  label:            z.string(),
  value:            z.number(),
  category:         z.enum(['High', 'Medium', 'Low']),
  federatedRound:   z.number(),
  confidence:       z.number(),
  calibration:      z.string(),
  shapFactors:      z.array(ShapFactorSchema).default([]),
  clinicalEvidence: z.array(ClinicalEvidenceSchema).default([]),
  createdAt:        z.string().nullable().optional(),
});
export type Prediction = z.infer<typeof PredictionSchema>;

// ── Paginated predictions ─────────────────────────────────────────────────

export const PredictionPageSchema = PageResponseSchema(PredictionSchema);
export type PredictionPage = z.infer<typeof PredictionPageSchema>;

// ── Risk distribution item (for donut chart) ──────────────────────────────

export const RiskDistributionItemSchema = z.object({
  name:  z.string(),
  value: z.number(),
});
export type RiskDistributionItem = z.infer<typeof RiskDistributionItemSchema>;

// ── Prediction stats (for KPI cards) ─────────────────────────────────────

export const PredictionStatsSchema = z.object({
  total:         z.number(),
  avgAccuracy:   z.number(),
  highRiskCount: z.number(),
  latestRound:   z.number(),
});
export type PredictionStats = z.infer<typeof PredictionStatsSchema>;

// ── Full SHAP explanation response ────────────────────────────────────────

export const ExplanationSchema = z.object({
  predictionId:            z.string(),
  patientId:               z.string(),
  patientName:             z.string(),
  model:                   z.string(),
  label:                   z.string(),
  value:                   z.number(),
  category:                z.enum(['High', 'Medium', 'Low']),
  federatedRound:          z.number(),
  confidence:              z.number(),
  calibration:             z.string(),
  shapFactors:             z.array(ShapFactorSchema).default([]),
  clinicalEvidence:        z.array(ClinicalEvidenceSchema).default([]),
  naturalLanguageSummary:  z.string(),
  createdAt:               z.string().nullable().optional(),
});
export type Explanation = z.infer<typeof ExplanationSchema>;

// ── Federated node ────────────────────────────────────────────────────────

export const FederatedNodeSchema = z.object({
  id:           z.string(),
  hospitalName: z.string(),
  status:       z.string(),
  patients:     z.number(),
  lastSync:     z.string().nullable().optional(),
  contribution: z.number(),
});
export type FederatedNode = z.infer<typeof FederatedNodeSchema>;

// ── Federated round node contribution ────────────────────────────────────

export const NodeContributionSchema = z.object({
  nodeId:       z.string(),
  contribution: z.number(),
  status:       z.string(),
});

// ── Federated round ───────────────────────────────────────────────────────

export const FederatedRoundSchema = z.object({
  round:             z.number(),
  model:             z.string(),
  accuracy:          z.number(),
  durationMinutes:   z.number(),
  status:            z.string(),
  startedAt:         z.string().nullable().optional(),
  completedAt:       z.string().nullable().optional(),
  nodeContributions: z.array(NodeContributionSchema).default([]),
});
export type FederatedRound = z.infer<typeof FederatedRoundSchema>;

// ── Paginated rounds ──────────────────────────────────────────────────────

export const FederatedRoundPageSchema = PageResponseSchema(FederatedRoundSchema);
export type FederatedRoundPage = z.infer<typeof FederatedRoundPageSchema>;

// ── Prediction filter criteria ────────────────────────────────────────────

export interface PredictionFilters {
  patientId?: string;
  category?:  string;
  model?:     string;
  page?:      number;
  size?:      number;
}
