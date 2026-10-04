import api from './client';
import {
  TwinSchema,
  BodyRegionSchema,
  DataSourcesSchema,
  TwinTimelineEventSchema,
  type Twin,
  type BodyRegion,
  type DataSources,
  type TwinTimelineEvent,
} from '../schemas/twin.schema';
import { z } from 'zod';

// ── Full twin ─────────────────────────────────────────────────────────────

export async function getTwin(twinId: string): Promise<Twin> {
  const response = await api.get(`/api/twins/${twinId}`);
  return TwinSchema.parse(response.data);
}

// ── Body regions ──────────────────────────────────────────────────────────

export async function getTwinBodyRegions(twinId: string): Promise<BodyRegion[]> {
  const response = await api.get(`/api/twins/${twinId}/body-regions`);
  return z.array(BodyRegionSchema).parse(response.data);
}

// ── Data sources ──────────────────────────────────────────────────────────

export async function getTwinDataSources(twinId: string): Promise<DataSources> {
  const response = await api.get(`/api/twins/${twinId}/data-sources`);
  return DataSourcesSchema.parse(response.data);
}

// ── Timeline ──────────────────────────────────────────────────────────────

export async function getTwinTimeline(twinId: string): Promise<TwinTimelineEvent[]> {
  const response = await api.get(`/api/twins/${twinId}/timeline`);
  return z.array(TwinTimelineEventSchema).parse(response.data);
}

// ── Sync ──────────────────────────────────────────────────────────────────

export async function syncTwin(twinId: string): Promise<Twin> {
  const response = await api.post(`/api/twins/${twinId}/sync`);
  return TwinSchema.parse(response.data);
}
