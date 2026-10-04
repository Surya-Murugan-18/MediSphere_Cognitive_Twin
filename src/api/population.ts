import api from './client';
import { z } from 'zod';
import {
  AdherenceTrendPointSchema,
  PopulationRiskTrendPointSchema,
  PopulationStatsSchema,
  HospitalSummarySchema,
  AlertTrendPointSchema,
  PopulationCategorySchema,
  RiskDistributionItemSchema,
  ConditionDistributionItemSchema,
  type PopulationStats,
  type HospitalSummary,
  type AlertTrendPoint,
  type PopulationCategory,
  type RiskDistributionItem,
  type ConditionDistributionItem,
  type AdherenceTrendPoint,
  type PopulationRiskTrendPoint,
} from '../schemas/population.schema';

/**
 * Population health API service.
 * Per design.md §10 and tasks.md F7.4.
 *
 * All 6 GET endpoints:
 *   /api/population/stats
 *   /api/population/risk-distribution
 *   /api/population/condition-distribution
 *   /api/population/hospitals
 *   /api/population/alert-trend
 *   /api/population/categories
 */

export async function getPopulationStats(): Promise<PopulationStats> {
  const response = await api.get('/api/population/stats');
  return PopulationStatsSchema.parse(response.data);
}

export async function getRiskDistribution(): Promise<RiskDistributionItem[]> {
  const response = await api.get('/api/population/risk-distribution');
  return z.array(RiskDistributionItemSchema).parse(response.data);
}

export async function getConditionDistribution(): Promise<ConditionDistributionItem[]> {
  const response = await api.get('/api/population/condition-distribution');
  return z.array(ConditionDistributionItemSchema).parse(response.data);
}

export async function getHospitalOverview(): Promise<HospitalSummary[]> {
  const response = await api.get('/api/population/hospitals');
  return z.array(HospitalSummarySchema).parse(response.data);
}

export async function getAlertTrend(): Promise<AlertTrendPoint[]> {
  const response = await api.get('/api/population/alert-trend');
  return z.array(AlertTrendPointSchema).parse(response.data);
}

export async function getPopulationCategories(): Promise<PopulationCategory[]> {
  const response = await api.get('/api/population/categories');
  return z.array(PopulationCategorySchema).parse(response.data);
}


export async function getPopulationAdherenceTrend(): Promise<AdherenceTrendPoint[]> {
  const response = await api.get('/api/population/adherence-trend');
  return z.array(AdherenceTrendPointSchema).parse(response.data);
}

export async function getPopulationRiskTrend(): Promise<PopulationRiskTrendPoint[]> {
  const response = await api.get('/api/population/risk-trend');
  return z.array(PopulationRiskTrendPointSchema).parse(response.data);
}
