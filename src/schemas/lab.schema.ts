import { z } from 'zod';
import { PageResponseSchema } from './patient.schema';

// ── Single lab result ─────────────────────────────────────────────────────

export const LabResultSchema = z.object({
  id:             z.string(),
  patientId:      z.string(),
  loinc:          z.string().nullable().optional(),
  test:           z.string(),
  result:         z.string(),
  numeric:        z.number(),
  unit:           z.string().nullable().optional(),
  referenceRange: z.string().nullable().optional(),
  status:         z.enum(['High', 'Low', 'Normal', 'Pending']),
  category:       z.enum(['Metabolic', 'Lipids', 'Hematology', 'Cardiac']),
  date:           z.string(),
  trend:          z.enum(['up', 'down', 'flat']),
  previous:       z.string().nullable().optional(),
  significance:   z.string().nullable().optional(),
});
export type LabResult = z.infer<typeof LabResultSchema>;

// ── Paginated lab results ─────────────────────────────────────────────────

export const LabResultPageSchema = PageResponseSchema(LabResultSchema);
export type LabResultPage = z.infer<typeof LabResultPageSchema>;

// ── Lab filter criteria ───────────────────────────────────────────────────

export interface LabFilters {
  category?: string;
  status?:   string;
  dateFrom?: string;
  dateTo?:   string;
  page?:     number;
  size?:     number;
}
