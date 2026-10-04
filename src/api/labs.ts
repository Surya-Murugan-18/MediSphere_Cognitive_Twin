import api from './client';
import {
  LabResultSchema,
  LabResultPageSchema,
  type LabResult,
  type LabResultPage,
  type LabFilters,
} from '../schemas/lab.schema';
import { z } from 'zod';

/**
 * Laboratory results API service.
 * All functions parse responses through Zod schemas.
 * Server-side filtering is performed via query params — no client-side array filtering.
 */

// ── Paginated filtered results ────────────────────────────────────────────

export async function getLabResults(
  patientId: string,
  filters: LabFilters = {},
): Promise<LabResultPage> {
  const params = new URLSearchParams();
  if (filters.category && filters.category !== 'All types')
    params.set('category', filters.category);
  if (filters.status && filters.status !== 'All statuses')
    params.set('status', filters.status);
  if (filters.dateFrom) params.set('dateFrom', filters.dateFrom);
  if (filters.dateTo)   params.set('dateTo',   filters.dateTo);
  params.set('page', String(filters.page ?? 0));
  params.set('size', String(filters.size ?? 50));

  const response = await api.get(
    `/api/patients/${patientId}/labs?${params.toString()}`,
  );
  return LabResultPageSchema.parse(response.data);
}

// ── Recent results (for Patient360 overview + labs tab) ───────────────────

export async function getRecentLabResults(
  patientId: string,
  limit = 3,
): Promise<LabResult[]> {
  const response = await api.get(
    `/api/patients/${patientId}/labs/recent?limit=${limit}`,
  );
  return z.array(LabResultSchema).parse(response.data);
}
