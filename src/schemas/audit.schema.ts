import { z } from 'zod';

export const AuditLogSchema = z.object({
  id: z.string(),
  timestamp: z.string().nullable().optional(),
  userId: z.string().nullable().optional(),
  userName: z.string().nullable().optional(),
  userRole: z.string().nullable().optional(),
  action: z.string().nullable().optional(),
  patientId: z.string().nullable().optional(),
  patientName: z.string().nullable().optional(),
  module: z.string().nullable().optional(),
  status: z.string().nullable().optional(),
  ipAddress: z.string().nullable().optional(),
});

export const AuditPageSchema = z.object({
  content: z.array(AuditLogSchema),
  totalElements: z.number(),
  totalPages: z.number(),
  page: z.number().optional(),
  size: z.number().optional(),
});

export type AuditLog = z.infer<typeof AuditLogSchema>;
export type AuditPage = z.infer<typeof AuditPageSchema>;

export interface AuditFilters {
  user?: string;
  patient?: string;
  action?: string;
  module?: string;
  from?: string;  // ISO date-time string
  to?: string;    // ISO date-time string
  page?: number;
  size?: number;
}
