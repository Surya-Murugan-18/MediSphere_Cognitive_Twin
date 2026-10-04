import api from './client';
import {
  AdherenceSchema,
  AdherenceTrendPointSchema,
  OutcomeSchema,
  type Adherence,
  type AdherenceTrendPoint,
  type Outcome,
} from '../schemas/carePlan.schema';
import { z } from 'zod';

/**
 * Adherence and outcomes API service.
 *
 * All functions use the shared Axios client (JWT injection, 401 refresh).
 * Responses are validated through Zod schemas.
 *
 * Per design.md §10 and tasks.md F6.1.
 */

// ── Adherence overview ────────────────────────────────────────────────────

export async function getAdherence(planId: string): Promise<Adherence> {
  const response = await api.get(`/api/care-plans/${planId}/adherence`);
  return AdherenceSchema.parse(response.data);
}

// ── Adherence trend ───────────────────────────────────────────────────────

export async function getAdherenceTrend(planId: string, weeks = 6): Promise<AdherenceTrendPoint[]> {
  const response = await api.get(`/api/care-plans/${planId}/adherence/trend?weeks=${weeks}`);
  return z.array(AdherenceTrendPointSchema).parse(response.data);
}

// ── Outcomes ──────────────────────────────────────────────────────────────

export async function getOutcomes(planId: string): Promise<Outcome[]> {
  const response = await api.get(`/api/care-plans/${planId}/outcomes`);
  return z.array(OutcomeSchema).parse(response.data);
}
