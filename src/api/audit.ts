import api from './client';
import {
  AuditPageSchema,
  type AuditPage,
  type AuditFilters,
} from '../schemas/audit.schema';

/**
 * Audit log API service.
 * Per design.md §10 and tasks.md F7.3.
 *
 * GET /api/audit            — paginated + filtered
 * GET /api/audit/export     — CSV download (triggers browser download)
 */

export async function getAuditLogs(filters: AuditFilters = {}): Promise<AuditPage> {
  const params = new URLSearchParams();
  if (filters.user)    params.set('user',    filters.user);
  if (filters.patient) params.set('patient', filters.patient);
  if (filters.action)  params.set('action',  filters.action);
  if (filters.module)  params.set('module',  filters.module);
  if (filters.from)    params.set('from',    filters.from);
  if (filters.to)      params.set('to',      filters.to);
  params.set('page', String(filters.page ?? 0));
  params.set('size', String(filters.size ?? 20));

  const response = await api.get(`/api/audit?${params.toString()}`);
  return AuditPageSchema.parse(response.data);
}

/**
 * Triggers a browser file download for the audit log CSV export.
 * ADMIN role only — server returns 403 for others.
 */
export function downloadAuditExport(filters: Omit<AuditFilters, 'page' | 'size'> = {}): void {
  const params = new URLSearchParams();
  if (filters.user)    params.set('user',    filters.user);
  if (filters.patient) params.set('patient', filters.patient);
  if (filters.action)  params.set('action',  filters.action);
  if (filters.module)  params.set('module',  filters.module);
  if (filters.from)    params.set('from',    filters.from);
  if (filters.to)      params.set('to',      filters.to);
  params.set('format', 'csv');

  // Navigate the browser to the export URL — the server streams a CSV attachment
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
  window.location.href = `${baseUrl}/api/audit/export?${params.toString()}`;
}
