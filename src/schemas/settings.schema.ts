import { z } from 'zod';

export const NotificationPrefsSchema = z.object({
  critical: z.boolean(),
  risk: z.boolean(),
  approvals: z.boolean(),
  system: z.boolean(),
});

export const FhirConfigSchema = z.object({
  mode: z.string(),
  baseUrl: z.string(),
  version: z.string(),
  authType: z.string(),
  syncIntervalMinutes: z.number(),
  updatedBy: z.string().nullable().optional(),
  updatedAt: z.string().nullable().optional(),
});

export type NotificationPrefs = z.infer<typeof NotificationPrefsSchema>;
export type FhirConfig = z.infer<typeof FhirConfigSchema>;

export interface UpdateProviderProfileData {
  name?: string;
  specialty?: string;
  facility?: string;
}

export interface UpdateNotificationPrefsData {
  critical?: boolean;
  risk?: boolean;
  approvals?: boolean;
  system?: boolean;
}

export interface UpdateFhirConfigData {
  mode?: string;
  baseUrl?: string;
  version?: string;
  authType?: string;
  syncIntervalMinutes?: number;
}
