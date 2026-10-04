import api from './client';
import { z } from 'zod';
import {
  SystemServiceSchema,
  SystemEventSchema,
  type SystemService,
  type SystemEvent,
} from '../schemas/system.schema';

/**
 * System status API service.
 * Per design.md §10 and tasks.md F7.6.
 *
 * GET /api/system/services — integration health statuses
 * GET /api/system/events   — recent system events
 */

export async function getSystemServices(): Promise<SystemService[]> {
  const response = await api.get('/api/system/services');
  return z.array(SystemServiceSchema).parse(response.data);
}

export async function getSystemEvents(): Promise<SystemEvent[]> {
  const response = await api.get('/api/system/events');
  return z.array(SystemEventSchema).parse(response.data);
}
