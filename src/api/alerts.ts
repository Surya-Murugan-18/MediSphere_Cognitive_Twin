import api from './client';
import {
  AlertSchema,
  AlertPageSchema,
  AlertCountSchema,
  type AlertFromApi,
  type AlertPage,
  type AlertCount,
  type AlertFilters,
} from '../schemas/alert.schema';

/**
 * Alert API service.
 *
 * All functions use the shared Axios client (JWT injection, refresh).
 * Responses are validated through Zod schemas.
 *
 * Per design.md §10 API contract and tasks.md F5.1.
 */

// ── List / count ──────────────────────────────────────────────────────────

export async function getAlerts(filters: AlertFilters = {}): Promise<AlertPage> {
  const params = new URLSearchParams();
  if (filters.severity)  params.set('severity',  filters.severity);
  if (filters.status)    params.set('status',     filters.status);
  if (filters.patientId) params.set('patientId',  filters.patientId);
  if (filters.type)      params.set('type',       filters.type);
  if (filters.from)      params.set('from',       filters.from);
  if (filters.to)        params.set('to',         filters.to);
  params.set('page', String(filters.page ?? 0));
  params.set('size', String(filters.size ?? 20));

  const response = await api.get(`/api/alerts?${params.toString()}`);
  return AlertPageSchema.parse(response.data);
}

/**
 * Returns the count of alerts for a given status.
 * Use status='Unacknowledged' for the sidebar badge.
 */
export async function getAlertCount(status?: string): Promise<AlertCount> {
  const url = status
    ? `/api/alerts/count?status=${encodeURIComponent(status)}`
    : '/api/alerts/count';
  const response = await api.get(url);
  return AlertCountSchema.parse(response.data);
}

// ── Single alert ──────────────────────────────────────────────────────────

export async function getAlert(alertId: string): Promise<AlertFromApi> {
  const response = await api.get(`/api/alerts/${alertId}`);
  return AlertSchema.parse(response.data);
}

// ── Patient-scoped alerts ─────────────────────────────────────────────────

export async function getPatientAlerts(patientId: string): Promise<AlertFromApi[]> {
  const response = await api.get(`/api/patients/${patientId}/alerts`);
  return response.data.map((a: unknown) => AlertSchema.parse(a));
}

// ── Lifecycle mutations ───────────────────────────────────────────────────

export async function acknowledgeAlert(
  alertId: string,
  notes?: string,
): Promise<AlertFromApi> {
  const response = await api.patch(`/api/alerts/${alertId}/acknowledge`, { notes: notes ?? null });
  return AlertSchema.parse(response.data);
}

export async function escalateAlert(
  alertId: string,
  reason: string,
  escalateTo: string,
): Promise<AlertFromApi> {
  const response = await api.post(`/api/alerts/${alertId}/escalate`, { reason, escalateTo });
  return AlertSchema.parse(response.data);
}

export async function resolveAlert(
  alertId: string,
  resolution: string,
): Promise<AlertFromApi> {
  const response = await api.post(`/api/alerts/${alertId}/resolve`, { resolution });
  return AlertSchema.parse(response.data);
}
