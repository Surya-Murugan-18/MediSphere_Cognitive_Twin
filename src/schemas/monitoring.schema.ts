import { z } from 'zod';

// ── Monitoring KPI stats ──────────────────────────────────────────────────

export const MonitoringStatsSchema = z.object({
  alertsToday:    z.number(),
  wearablesOnline: z.number(),
  avgResponseMin:  z.number(),
  streamStatus:    z.string(),
});
export type MonitoringStats = z.infer<typeof MonitoringStatsSchema>;

// ── Kafka event entry ─────────────────────────────────────────────────────

export const KafkaEventEntrySchema = z.object({
  id:        z.string(),
  time:      z.string(),
  topic:     z.string(),
  text:      z.string(),
  timestamp: z.string().nullable().optional(),
});
export type KafkaEventEntry = z.infer<typeof KafkaEventEntrySchema>;

// ── Kafka stream stats ────────────────────────────────────────────────────

export const KafkaStatsSchema = z.object({
  eventsPerSec:  z.number(),
  consumerLag:   z.string(),
  latestEvents:  z.array(KafkaEventEntrySchema).default([]),
});
export type KafkaStats = z.infer<typeof KafkaStatsSchema>;
