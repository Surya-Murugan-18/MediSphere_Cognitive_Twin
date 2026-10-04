import { z } from 'zod';

export const ReportCardSchema = z.object({
  id: z.string(),
  title: z.string(),
  detail: z.string(),
  lastRun: z.string(),
  rows: z.string(),
  ready: z.boolean(),
  lastRunAt: z.string().nullable().optional(),
});

export const ReportJobStatusSchema = z.object({
  jobId: z.string(),
  reportId: z.string(),
  /** PENDING | RUNNING | COMPLETED | FAILED */
  status: z.string(),
  progress: z.number(),
  requestedAt: z.string().nullable().optional(),
  completedAt: z.string().nullable().optional(),
  errorMessage: z.string().nullable().optional(),
});

export type ReportCard = z.infer<typeof ReportCardSchema>;
export type ReportJobStatus = z.infer<typeof ReportJobStatusSchema>;
