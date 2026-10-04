import api from './client';
import { z } from 'zod';
import {
  PredictionSchema,
  PredictionPageSchema,
  PredictionStatsSchema,
  RiskDistributionItemSchema,
  FederatedNodeSchema,
  FederatedRoundSchema,
  FederatedRoundPageSchema,
  ShapFactorSchema,
  type Prediction,
  type PredictionPage,
  type PredictionStats,
  type RiskDistributionItem,
  type FederatedNode,
  type FederatedRound,
  type FederatedRoundPage,
  type ShapFactor,
  type PredictionFilters,
} from '../schemas/prediction.schema';

/**
 * Predictions API service — F4.1
 *
 * All functions:
 *   - Call the backend through the shared Axios client (with JWT auth)
 *   - Parse responses through Zod schemas
 *   - Never compute, generate, or fabricate prediction values
 */

// ── Paginated prediction list ─────────────────────────────────────────────

export async function getPredictions(
  filters: PredictionFilters = {},
): Promise<PredictionPage> {
  const params = new URLSearchParams();
  if (filters.patientId) params.set('patientId', filters.patientId);
  if (filters.category && filters.category !== 'All')
    params.set('category', filters.category);
  if (filters.model) params.set('model', filters.model);
  params.set('page', String(filters.page ?? 0));
  params.set('size', String(filters.size ?? 20));

  const response = await api.get(`/api/predictions?${params.toString()}`);
  return PredictionPageSchema.parse(response.data);
}

// ── Single prediction ─────────────────────────────────────────────────────

export async function getPrediction(id: string): Promise<Prediction> {
  const response = await api.get(`/api/predictions/${id}`);
  return PredictionSchema.parse(response.data);
}

// ── All predictions for a patient ────────────────────────────────────────

export async function getPatientPredictions(patientId: string): Promise<Prediction[]> {
  const response = await api.get(`/api/patients/${patientId}/predictions`);
  return z.array(PredictionSchema).parse(response.data);
}

// ── Risk distribution (for donut chart) ──────────────────────────────────

export async function getRiskDistribution(): Promise<RiskDistributionItem[]> {
  const response = await api.get('/api/predictions/risk-distribution');
  return z.array(RiskDistributionItemSchema).parse(response.data);
}

// ── KPI stats (for Predictions page header cards) ─────────────────────────

export async function getPredictionStats(): Promise<PredictionStats> {
  const response = await api.get('/api/predictions/stats');
  return PredictionStatsSchema.parse(response.data);
}

// ── Trigger a prediction run ──────────────────────────────────────────────

export async function runPrediction(
  patientId: string,
  model: string = 'ALL',
): Promise<Prediction[]> {
  const response = await api.post('/api/predictions/run', { patientId, model });
  return z.array(PredictionSchema).parse(response.data);
}

// ── SHAP factors for a prediction ────────────────────────────────────────

export async function getShapFactors(predictionId: string): Promise<ShapFactor[]> {
  const response = await api.get(`/api/predictions/${predictionId}/shap`);
  return z.array(ShapFactorSchema).parse(response.data);
}

// ── Federated learning nodes ──────────────────────────────────────────────

export async function getFederatedNodes(): Promise<FederatedNode[]> {
  const response = await api.get('/api/federated/nodes');
  return z.array(FederatedNodeSchema).parse(response.data);
}

// ── Current federated round ───────────────────────────────────────────────

export async function getCurrentFederatedRound(): Promise<FederatedRound> {
  const response = await api.get('/api/federated/rounds/current');
  return FederatedRoundSchema.parse(response.data);
}

// ── Federated round history ───────────────────────────────────────────────

export async function getFederatedRounds(
  page = 0,
  size = 10,
): Promise<FederatedRoundPage> {
  const response = await api.get(`/api/federated/rounds?page=${page}&size=${size}`);
  return FederatedRoundPageSchema.parse(response.data);
}
