import api from './client';
import {
  CarePlanSchema,
  CarePlanPageSchema,
  CarePlanStatsSchema,
  CarePlanTimelineEventSchema,
  type CarePlan,
  type CarePlanPage,
  type CarePlanStats,
  type CarePlanTimelineEvent,
  type CarePlanFilters,
} from '../schemas/carePlan.schema';
import { z } from 'zod';

/**
 * Care plan API service.
 *
 * All functions use the shared Axios client (JWT injection, 401 refresh).
 * Responses are validated through Zod schemas.
 *
 * Per design.md §10 and tasks.md F6.1.
 */

// ── List / filtered ───────────────────────────────────────────────────────

export async function getCarePlans(filters: CarePlanFilters = {}): Promise<CarePlanPage> {
  const params = new URLSearchParams();
  if (filters.patientId) params.set('patientId', filters.patientId);
  if (filters.status)    params.set('status',    filters.status);
  if (filters.risk)      params.set('risk',      filters.risk);
  params.set('page', String(filters.page ?? 0));
  params.set('size', String(filters.size ?? 20));

  const response = await api.get(`/api/care-plans?${params.toString()}`);
  return CarePlanPageSchema.parse(response.data);
}

// ── Generate ──────────────────────────────────────────────────────────────

export async function generateCarePlan(patientId: string): Promise<CarePlan> {
  const response = await api.post('/api/care-plans/generate', { patientId });
  return CarePlanSchema.parse(response.data);
}

// ── Single plan ───────────────────────────────────────────────────────────

export async function getCarePlan(id: string): Promise<CarePlan> {
  const response = await api.get(`/api/care-plans/${id}`);
  return CarePlanSchema.parse(response.data);
}

// ── Update (edit mode) ────────────────────────────────────────────────────

export async function updateCarePlan(id: string, data: { goal?: string; riskLevel?: string }): Promise<CarePlan> {
  const response = await api.put(`/api/care-plans/${id}`, data);
  return CarePlanSchema.parse(response.data);
}

// ── Approve ───────────────────────────────────────────────────────────────

export async function approveCarePlan(id: string, notes?: string): Promise<CarePlan> {
  const response = await api.post(`/api/care-plans/${id}/approve`, { notes: notes ?? null });
  return CarePlanSchema.parse(response.data);
}

// ── Reject ────────────────────────────────────────────────────────────────

export async function rejectCarePlan(id: string, reason: string): Promise<CarePlan> {
  const response = await api.post(`/api/care-plans/${id}/reject`, { reason });
  return CarePlanSchema.parse(response.data);
}

// ── Stats ─────────────────────────────────────────────────────────────────

export async function getCarePlanStats(): Promise<CarePlanStats> {
  const response = await api.get('/api/care-plans/stats');
  return CarePlanStatsSchema.parse(response.data);
}

// ── Timeline ──────────────────────────────────────────────────────────────

export async function getCarePlanTimeline(id: string): Promise<CarePlanTimelineEvent[]> {
  const response = await api.get(`/api/care-plans/${id}/timeline`);
  return z.array(CarePlanTimelineEventSchema).parse(response.data);
}

// ── Active plan for patient ───────────────────────────────────────────────

/**
 * Returns the ACTIVE care plan for a patient.
 * Throws an Axios error (404) if no active plan exists — caller should handle.
 */
export async function getActiveCarePlan(patientId: string): Promise<CarePlan> {
  const response = await api.get(`/api/patients/${patientId}/care-plan`);
  return CarePlanSchema.parse(response.data);
}
