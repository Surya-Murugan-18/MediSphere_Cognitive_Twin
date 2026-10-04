import { z } from 'zod';
import { PageResponseSchema } from './patient.schema';

// ── Care plan status ──────────────────────────────────────────────────────

export const CarePlanStatusSchema = z.enum(['DRAFT', 'ACTIVE', 'REJECTED']);
export type CarePlanStatus = z.infer<typeof CarePlanStatusSchema>;

// ── Embedded recommendation ───────────────────────────────────────────────

export const CarePlanRecommendationSchema = z.object({
  id:           z.string(),
  title:        z.string(),
  goal:         z.string(),
  intervention: z.string(),
  monitoring:   z.string(),
  outcome:      z.string(),
  evidence:     z.string(),
});
export type CarePlanRecommendation = z.infer<typeof CarePlanRecommendationSchema>;

// ── Embedded safety check ─────────────────────────────────────────────────

export const SafetyCheckSchema = z.object({
  id:     z.string(),
  label:  z.string(),
  detail: z.string(),
  tone:   z.string(),   // healthy | warning | critical
});
export type SafetyCheck = z.infer<typeof SafetyCheckSchema>;

// ── Predicted outcome ─────────────────────────────────────────────────────

export const PredictedOutcomeSchema = z.object({
  metric: z.string(),
  before: z.number(),
  after:  z.number(),
  unit:   z.string(),
}).nullable().optional();
export type PredictedOutcome = z.infer<typeof PredictedOutcomeSchema>;

// ── Full care plan ────────────────────────────────────────────────────────

export const CarePlanSchema = z.object({
  id:               z.string(),
  patientId:        z.string(),
  patientName:      z.string(),
  goal:             z.string(),
  riskLevel:        z.string(),
  adherence:        z.number(),
  status:           CarePlanStatusSchema,
  providerId:       z.string().nullable().optional(),
  generatedBy:      z.string(),
  recommendations:  z.array(CarePlanRecommendationSchema).default([]),
  safetyChecks:     z.array(SafetyCheckSchema).default([]),
  predictedOutcome: PredictedOutcomeSchema,
  // Approval
  approvedBy:       z.string().nullable().optional(),
  approvedByName:   z.string().nullable().optional(),
  approvedAt:       z.string().nullable().optional(),
  approvalNotes:    z.string().nullable().optional(),
  // Rejection
  rejectionReason:  z.string().nullable().optional(),
  createdAt:        z.string().nullable().optional(),
  updatedAt:        z.string().nullable().optional(),
});
export type CarePlan = z.infer<typeof CarePlanSchema>;

// ── Paginated care plans ──────────────────────────────────────────────────

export const CarePlanPageSchema = PageResponseSchema(CarePlanSchema);
export type CarePlanPage = z.infer<typeof CarePlanPageSchema>;

// ── Care plan stats ───────────────────────────────────────────────────────

export const CarePlanStatsSchema = z.object({
  activeCount:              z.number(),
  avgAdherence:             z.number(),
  hospitalizationReduction: z.number(),
  draftCount:               z.number(),
  rejectedCount:            z.number(),
});
export type CarePlanStats = z.infer<typeof CarePlanStatsSchema>;

// ── Timeline event ────────────────────────────────────────────────────────

export const CarePlanTimelineEventSchema = z.object({
  id:        z.string(),
  timestamp: z.string().nullable().optional(),
  title:     z.string(),
  detail:    z.string().nullable().optional(),
  tone:      z.string(),
});
export type CarePlanTimelineEvent = z.infer<typeof CarePlanTimelineEventSchema>;

// ── Adherence breakdown item ──────────────────────────────────────────────

export const AdherenceBreakdownItemSchema = z.object({
  label: z.string(),
  value: z.number(),
});

// ── Full adherence response ───────────────────────────────────────────────

export const AdherenceSchema = z.object({
  planId:    z.string(),
  overall:   z.number(),
  breakdown: z.array(AdherenceBreakdownItemSchema).default([]),
});
export type Adherence = z.infer<typeof AdherenceSchema>;

// ── Adherence trend point ─────────────────────────────────────────────────

export const AdherenceTrendPointSchema = z.object({
  t:     z.string(),    // x-axis label e.g. "Week 1"
  value: z.number(),
});
export type AdherenceTrendPoint = z.infer<typeof AdherenceTrendPointSchema>;

// ── Outcome ───────────────────────────────────────────────────────────────

export const OutcomeSchema = z.object({
  id:        z.string(),
  planId:    z.string(),
  metric:    z.string(),
  baseline:  z.number(),
  current:   z.number(),
  goal:      z.number(),
  unit:      z.string(),
  trend:     z.string(),
  updatedAt: z.string().nullable().optional(),
});
export type Outcome = z.infer<typeof OutcomeSchema>;

// ── Filter criteria ───────────────────────────────────────────────────────

export interface CarePlanFilters {
  patientId?: string;
  status?:    string;
  risk?:      string;
  page?:      number;
  size?:      number;
}
