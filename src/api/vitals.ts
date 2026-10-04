import api from './client';
import {
  VitalsSnapshotSchema,
  VitalsHistorySchema,
  WearableDeviceSchema,
  type VitalsSnapshot,
  type VitalsHistory,
  type WearableDevice,
} from '../schemas/vitals.schema';
export type {
  VitalsSnapshot,
  VitalsHistory,
  WearableDevice,
};
/**
 * Vitals API service.
 * All functions parse responses through Zod schemas.
 * No direct fetch calls — uses the shared Axios client with JWT injection.
 */

// ── Current vitals snapshot ───────────────────────────────────────────────

export async function getCurrentVitals(patientId: string): Promise<VitalsSnapshot> {
  const response = await api.get(`/api/patients/${patientId}/vitals/current`);
  return VitalsSnapshotSchema.parse(response.data);
}

// ── Historical time-series ────────────────────────────────────────────────

/**
 * @param type   heartRate | bloodPressureSystolic | bloodPressureDiastolic | spo2 | temperature | respiratoryRate
 * @param period 24h (default) | 7d | 30d
 */
export async function getVitalsHistory(
  patientId: string,
  type: string,
  period: string,
): Promise<VitalsHistory> {
  const response = await api.get(
    `/api/patients/${patientId}/vitals/history?type=${encodeURIComponent(type)}&period=${encodeURIComponent(period)}`,
  );
  return VitalsHistorySchema.parse(response.data);
}

// ── Wearable device ───────────────────────────────────────────────────────

export async function getWearableDevice(patientId: string): Promise<WearableDevice> {
  const response = await api.get(`/api/devices/by-patient/${patientId}`);
  return WearableDeviceSchema.parse(response.data);
}
