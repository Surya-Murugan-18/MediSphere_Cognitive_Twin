import api from './client';
import { z } from 'zod';
import {
  ConsentSchema,
  ConsentHistoryEntrySchema,
  type ConsentFromApi,
  type ConsentHistoryEntry,
} from '../schemas/consent.schema';

/**
 * Consent API service.
 * Per design.md §10 and tasks.md F7.2.
 *
 * GET  /api/patients/{id}/consent
 * PUT  /api/patients/{id}/consent
 * GET  /api/patients/{id}/consent/history
 */

export async function getPatientConsent(patientId: string): Promise<ConsentFromApi> {
  const response = await api.get(`/api/patients/${patientId}/consent`);
  return ConsentSchema.parse(response.data);
}

export async function updateConsent(
  patientId: string,
  data: { ehr?: boolean; wearable?: boolean; ai?: boolean },
): Promise<ConsentFromApi> {
  const response = await api.put(`/api/patients/${patientId}/consent`, data);
  return ConsentSchema.parse(response.data);
}

export async function getConsentHistory(patientId: string): Promise<ConsentHistoryEntry[]> {
  const response = await api.get(`/api/patients/${patientId}/consent/history`);
  return z.array(ConsentHistoryEntrySchema).parse(response.data);
}
