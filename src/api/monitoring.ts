import api from './client';
import {
  MonitoringStatsSchema,
  KafkaStatsSchema,
  KafkaEventEntrySchema,
  type MonitoringStats,
  type KafkaStats,
  type KafkaEventEntry,
} from '../schemas/monitoring.schema';
import { z } from 'zod';

/**
 * Monitoring API service.
 *
 * Per design.md §10 and tasks.md F5.1:
 *   GET /api/monitoring/stats
 *   GET /api/monitoring/kafka-stats
 *   GET /api/monitoring/kafka-events?limit=
 */

export async function getMonitoringStats(): Promise<MonitoringStats> {
  const response = await api.get('/api/monitoring/stats');
  return MonitoringStatsSchema.parse(response.data);
}

export async function getKafkaStats(): Promise<KafkaStats> {
  const response = await api.get('/api/monitoring/kafka-stats');
  return KafkaStatsSchema.parse(response.data);
}

export async function getKafkaEvents(limit = 20): Promise<KafkaEventEntry[]> {
  const response = await api.get(`/api/monitoring/kafka-events?limit=${limit}`);
  return z.array(KafkaEventEntrySchema).parse(response.data);
}
