import { z } from 'zod';

export const PopulationStatsSchema = z.object({
  totalPatients: z.number(),
  highRiskCount: z.number(),
  mediumRiskCount: z.number(),
  lowRiskCount: z.number(),
  avgAdherence: z.number(),
  activeAlerts: z.number(),
  activePlans: z.number(),
  hospitalizationReduction: z.string(),
});

export const HospitalSummarySchema = z.object({
  name: z.string(),
  patients: z.number(),
  highRisk: z.number(),
  adherence: z.number(),
  alerts: z.number(),
});

export const AlertTrendPointSchema = z.object({
  t: z.string(),
  alerts: z.number(),
  critical: z.number(),
});

export const PopulationCategorySchema = z.object({
  name: z.string(),
  cohort: z.number(),
  highRisk: z.number(),
  trend: z.string(),
  tone: z.string(),
});

export const RiskDistributionItemSchema = z.object({
  name: z.string(),
  value: z.number(),
  tone: z.string().optional(),
});

export const ConditionDistributionItemSchema = z.object({
  name: z.string(),
  value: z.number(),
});

export type PopulationStats = z.infer<typeof PopulationStatsSchema>;
export type HospitalSummary = z.infer<typeof HospitalSummarySchema>;
export type AlertTrendPoint = z.infer<typeof AlertTrendPointSchema>;
export type PopulationCategory = z.infer<typeof PopulationCategorySchema>;
export type RiskDistributionItem = z.infer<typeof RiskDistributionItemSchema>;
export type ConditionDistributionItem = z.infer<typeof ConditionDistributionItemSchema>;


export const AdherenceTrendPointSchema = z.object({
  t: z.string(),
  value: z.number(),
});

export const PopulationRiskTrendPointSchema = z.object({
  t: z.string(),
  high: z.number(),
  medium: z.number(),
});

export type AdherenceTrendPoint = z.infer<typeof AdherenceTrendPointSchema>;
export type PopulationRiskTrendPoint = z.infer<typeof PopulationRiskTrendPointSchema>;
