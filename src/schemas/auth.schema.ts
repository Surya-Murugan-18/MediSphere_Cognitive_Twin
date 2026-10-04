import { z } from 'zod';

// ── ProviderRole ──────────────────────────────────────────────────────────

export const ProviderRoleSchema = z.enum(['ADMIN', 'CLINICIAN', 'NURSE', 'ANALYST']);
export type ProviderRole = z.infer<typeof ProviderRoleSchema>;

// ── NotificationPrefs ─────────────────────────────────────────────────────

export const NotificationPrefsSchema = z.object({
  critical: z.boolean(),
  risk: z.boolean(),
  approvals: z.boolean(),
  system: z.boolean(),
});
export type NotificationPrefs = z.infer<typeof NotificationPrefsSchema>;

// ── ProviderResponse ──────────────────────────────────────────────────────

export const ProviderResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  email: z.string().email(),
  role: ProviderRoleSchema,
  specialty: z.string().nullable().optional(),
  facility: z.string().nullable().optional(),
  npi: z.string().nullable().optional(),
  notificationPrefs: NotificationPrefsSchema.optional(),
  lastSignIn: z.string().nullable().optional(),
  active: z.boolean(),
});
export type ProviderResponse = z.infer<typeof ProviderResponseSchema>;

// ── AuthResponse ──────────────────────────────────────────────────────────

export const AuthResponseSchema = z.object({
  accessToken: z.string().min(1),
  provider: ProviderResponseSchema,
});
export type AuthResponse = z.infer<typeof AuthResponseSchema>;

// ── LoginRequest ──────────────────────────────────────────────────────────

export const LoginRequestSchema = z.object({
  email: z.string().min(1, 'Provider ID or email is required').email('Must be a valid email address'),
  password: z.string().min(1, 'Password is required'),
});
export type LoginRequest = z.infer<typeof LoginRequestSchema>;
