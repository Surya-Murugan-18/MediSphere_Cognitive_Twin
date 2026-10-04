import api from './client';
import { z } from 'zod';
import {
  ReportCardSchema,
  ReportJobStatusSchema,
  type ReportCard,
  type ReportJobStatus,
} from '../schemas/report.schema';

/**
 * Reports API service.
 * Per design.md §10 and tasks.md F7.5.
 *
 * GET  /api/reports                     — report card list
 * POST /api/reports/{id}/generate       — start async generation → { jobId, reportId }
 * GET  /api/reports/{id}/status?jobId=  — poll job status
 * GET  /api/reports/{id}/export         — file download
 */

export async function getReports(): Promise<ReportCard[]> {
  const response = await api.get('/api/reports');
  return z.array(ReportCardSchema).parse(response.data);
}

export async function generateReport(reportId: string): Promise<{ jobId: string; reportId: string }> {
  const response = await api.post(`/api/reports/${reportId}/generate`);
  return response.data as { jobId: string; reportId: string };
}

export async function getReportStatus(reportId: string, jobId: string): Promise<ReportJobStatus> {
  const response = await api.get(`/api/reports/${reportId}/status?jobId=${encodeURIComponent(jobId)}`);
  return ReportJobStatusSchema.parse(response.data);
}

/**
 * Triggers a browser file download for a completed report.
 * Uses window.location to stream the binary response as a file attachment.
 */
export function downloadReport(reportId: string, format: 'pdf' | 'csv' = 'pdf'): void {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
  window.location.href = `${baseUrl}/api/reports/${reportId}/export?format=${format}`;
}
