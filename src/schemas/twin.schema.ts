import { z } from 'zod';

// ── Data source entry ─────────────────────────────────────────────────────

export const DataSourceEntrySchema = z.object({
  connected: z.boolean(),
  lastSync: z.string().nullable().optional(),
});

export const DataSourcesSchema = z.object({
  ehr:      DataSourceEntrySchema,
  lab:      DataSourceEntrySchema,
  wearable: DataSourceEntrySchema,
  kafka:    DataSourceEntrySchema,
});
export type DataSources = z.infer<typeof DataSourcesSchema>;

// ── Body region ───────────────────────────────────────────────────────────

export const BodyRegionSchema = z.object({
  region:    z.string(),  // cardiac | vascular | metabolic | renal | respiratory
  label:     z.string(),
  detail:    z.string(),
  riskLevel: z.string(),  // high | medium | low
});
export type BodyRegion = z.infer<typeof BodyRegionSchema>;

// ── Timeline event ────────────────────────────────────────────────────────

export const TwinTimelineEventSchema = z.object({
  id:        z.string(),
  timestamp: z.string().nullable().optional(),
  title:     z.string(),
  detail:    z.string(),
  tone:      z.string(),
});
export type TwinTimelineEvent = z.infer<typeof TwinTimelineEventSchema>;

// ── Full twin response ────────────────────────────────────────────────────

export const TwinSchema = z.object({
  id:            z.string(),
  patientId:     z.string(),
  modelVersion:  z.string(),
  completeness:  z.number(),
  status:        z.string(),
  lastUpdated:   z.string().nullable().optional(),
  stateVersion:  z.number(),
  dataSources:   DataSourcesSchema,
  bodyRegions:   z.array(BodyRegionSchema).default([]),
  timeline:      z.array(TwinTimelineEventSchema).default([]),
});
export type Twin = z.infer<typeof TwinSchema>;
