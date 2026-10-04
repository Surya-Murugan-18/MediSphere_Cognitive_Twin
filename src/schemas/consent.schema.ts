import { z } from 'zod';

export const ConsentHistoryEntrySchema = z.object({
  id: z.string(),
  date: z.string().nullable().optional(),
  type: z.string(),
  status: z.string(),
  updatedBy: z.string().nullable().optional(),
});

export const ConsentSchema = z.object({
  id: z.string(),
  patientId: z.string(),
  ehr: z.boolean(),
  wearable: z.boolean(),
  ai: z.boolean(),
  updatedAt: z.string().nullable().optional(),
  updatedBy: z.string().nullable().optional(),
});

export type ConsentFromApi = z.infer<typeof ConsentSchema>;
export type ConsentHistoryEntry = z.infer<typeof ConsentHistoryEntrySchema>;
