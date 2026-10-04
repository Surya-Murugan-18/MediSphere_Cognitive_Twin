import api from './client';
import {
  NotificationPrefsSchema,
  FhirConfigSchema,
  type NotificationPrefs,
  type FhirConfig,
  type UpdateProviderProfileData,
  type UpdateNotificationPrefsData,
  type UpdateFhirConfigData,
} from '../schemas/settings.schema';
import { ProviderResponseSchema, type ProviderResponse } from '../schemas/auth.schema';

/**
 * Settings API service.
 * Per design.md §10 and tasks.md F7.7.
 *
 * GET  /api/providers/me                  — own profile
 * PUT  /api/providers/me                  — update own profile
 * GET  /api/providers/me/notifications    — notification prefs
 * PUT  /api/providers/me/notifications    — update notification prefs
 * GET  /api/settings/fhir                 — FHIR config (ADMIN only)
 * PUT  /api/settings/fhir                 — update FHIR config (ADMIN only)
 */

export async function getProviderProfile(): Promise<ProviderResponse> {
  const response = await api.get('/api/providers/me');
  return ProviderResponseSchema.parse(response.data);
}

export async function updateProviderProfile(
  data: UpdateProviderProfileData,
): Promise<ProviderResponse> {
  const response = await api.put('/api/providers/me', data);
  return ProviderResponseSchema.parse(response.data);
}

export async function getNotificationPrefs(): Promise<NotificationPrefs> {
  const response = await api.get('/api/providers/me/notifications');
  return NotificationPrefsSchema.parse(response.data);
}

export async function updateNotificationPrefs(
  data: UpdateNotificationPrefsData,
): Promise<NotificationPrefs> {
  const response = await api.put('/api/providers/me/notifications', data);
  return NotificationPrefsSchema.parse(response.data);
}

export async function getFhirConfig(): Promise<FhirConfig> {
  const response = await api.get('/api/settings/fhir');
  return FhirConfigSchema.parse(response.data);
}

export async function updateFhirConfig(data: UpdateFhirConfigData): Promise<FhirConfig> {
  const response = await api.put('/api/settings/fhir', data);
  return FhirConfigSchema.parse(response.data);
}
