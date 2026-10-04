import { z } from 'zod';

// ── Current vitals snapshot ───────────────────────────────────────────────

export const VitalsSnapshotSchema = z.object({
  patientId:      z.string(),
  heartRate:      z.number(),
  bloodPressure:  z.string(),
  spo2:           z.number(),
  temperature:    z.number(),
  respiratoryRate: z.number(),
  source:         z.string().nullable().optional(),
  deviceId:       z.string().nullable().optional(),
  updatedAt:      z.string().nullable().optional(),
});
export type VitalsSnapshot = z.infer<typeof VitalsSnapshotSchema>;

// ── Vitals history data point ─────────────────────────────────────────────

export const VitalsDataPointSchema = z.object({
  t:         z.string(),          // time label e.g. "14:30" or "Mon"
  value:     z.number(),
  timestamp: z.string().nullable().optional(),
});
export type VitalsDataPoint = z.infer<typeof VitalsDataPointSchema>;

// ── Vitals history response ───────────────────────────────────────────────

export const VitalsHistorySchema = z.object({
  patientId: z.string(),
  type:      z.string(),
  period:    z.string().nullable().optional(),
  data:      z.array(VitalsDataPointSchema),
});
export type VitalsHistory = z.infer<typeof VitalsHistorySchema>;

// ── Wearable device ───────────────────────────────────────────────────────

export const WearableDeviceSchema = z.object({
  id:           z.string().nullable().optional(),
  patientId:    z.string(),
  displayName:  z.string(),
  deviceType:   z.string().nullable().optional(),
  manufacturer: z.string().nullable().optional(),
  kafkaTopic:   z.string().nullable().optional(),
  deviceKey:    z.string().nullable().optional(),
  status:       z.string(),   // Online | Offline
  lastSeen:     z.string().nullable().optional(),
  registeredAt: z.string().nullable().optional(),
});
export type WearableDevice = z.infer<typeof WearableDeviceSchema>;
