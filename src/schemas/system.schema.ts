import { z } from 'zod';

export const SystemServiceSchema = z.object({
  name: z.string(),
  state: z.string(),
  uptime: z.string(),
  detail: z.string(),
  /** healthy | warning | critical | neutral */
  tone: z.string(),
});

export const SystemEventSchema = z.object({
  id: z.string(),
  timestamp: z.string().nullable().optional(),
  text: z.string(),
  tone: z.string(),
  category: z.string().nullable().optional(),
});

export type SystemService = z.infer<typeof SystemServiceSchema>;
export type SystemEvent = z.infer<typeof SystemEventSchema>;
