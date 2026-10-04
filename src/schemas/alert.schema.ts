import { z } from 'zod';
import { PageResponseSchema } from './patient.schema';

// ── Embedded audit trail entry ────────────────────────────────────────────

export const AlertAuditEntrySchema = z.object({
  id:        z.string(),
  timestamp: z.string().nullable().optional(),
  actor:     z.string(),
  actorId:   z.string().nullable().optional(),
  action:    z.string(),
});
export type AlertAuditEntry = z.infer<typeof AlertAuditEntrySchema>;

// ── Single alert ──────────────────────────────────────────────────────────

export const AlertSchema = z.object({
  id:               z.string(),
  severity:         z.enum(['HIGH', 'MEDIUM', 'LOW']),
  patientId:        z.string(),
  patientName:      z.string(),
  event:            z.string(),
  analysis:         z.string().nullable().optional(),
  type:             z.string(),
  ruleCode:         z.string().nullable().optional(),
  detectedAt:       z.string().nullable().optional(),
  status:           z.enum(['Unacknowledged', 'Acknowledged', 'Escalated', 'Resolved']),
  assignedProvider: z.string().nullable().optional(),
  currentValue:     z.string().nullable().optional(),
  previousValue:    z.string().nullable().optional(),
  confidence:       z.number(),
  // Acknowledge
  acknowledgedAt:    z.string().nullable().optional(),
  acknowledgedBy:    z.string().nullable().optional(),
  acknowledgeNotes:  z.string().nullable().optional(),
  // Escalate
  escalatedAt:       z.string().nullable().optional(),
  escalatedBy:       z.string().nullable().optional(),
  escalationReason:  z.string().nullable().optional(),
  escalatedTo:       z.string().nullable().optional(),
  // Resolve
  resolvedAt:        z.string().nullable().optional(),
  resolvedBy:        z.string().nullable().optional(),
  resolution:        z.string().nullable().optional(),
  // Embedded audit trail
  auditTrail:       z.array(AlertAuditEntrySchema).default([]),
  createdAt:        z.string().nullable().optional(),
});
export type AlertFromApi = z.infer<typeof AlertSchema>;

// ── Paginated alerts ──────────────────────────────────────────────────────

export const AlertPageSchema = PageResponseSchema(AlertSchema);
export type AlertPage = z.infer<typeof AlertPageSchema>;

// ── Alert count ───────────────────────────────────────────────────────────

export const AlertCountSchema = z.object({ count: z.number() });
export type AlertCount = z.infer<typeof AlertCountSchema>;

// ── Alert filter criteria ─────────────────────────────────────────────────

export interface AlertFilters {
  severity?:  string;
  status?:    string;
  patientId?: string;
  type?:      string;
  from?:      string;
  to?:        string;
  page?:      number;
  size?:      number;
}
