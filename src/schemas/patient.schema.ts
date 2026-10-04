import { z } from 'zod';

// ── Shared enums ──────────────────────────────────────────────────────────

export const RiskLevelSchema = z.enum(['High', 'Medium', 'Low']);
export type RiskLevel = z.infer<typeof RiskLevelSchema>;

export const TwinStatusSchema = z.enum(['Synchronized', 'Syncing', 'Stale', 'Not Created']);
export type TwinStatus = z.infer<typeof TwinStatusSchema>;

export const PatientStatusSchema = z.enum(['Active', 'Inactive', 'Pending Consent']);
export type PatientStatus = z.infer<typeof PatientStatusSchema>;

// ── Patient summary (list view) ───────────────────────────────────────────

export const PatientSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
  dob: z.string().nullable().optional(),
  gender: z.string().nullable().optional(),
  conditions: z.array(z.string()).default([]),
  riskLevel: z.string(),
  status: z.string(),
  twinId: z.string().nullable().optional(),
  twinStatus: z.string(),
  twinCompleteness: z.number(),
  fhirConnected: z.boolean(),
  consentComplete: z.boolean(),
  wearableStatus: z.string(),
  providerName: z.string().nullable().optional(),
  updatedAt: z.string().nullable().optional(),
});
export type PatientSummary = z.infer<typeof PatientSummarySchema>;

// ── Vital snapshot (Phase 3+ — nullable in Phase 2) ───────────────────────

export const VitalSnapshotSchema = z.object({
  heartRate: z.number(),
  bloodPressure: z.string(),
  spo2: z.number(),
  temperature: z.number(),
  respiratoryRate: z.number(),
}).nullable().optional();

// ── Medication (Phase 3+ — nullable in Phase 2) ───────────────────────────

export const MedicationSchema = z.object({
  name: z.string(),
  dose: z.string(),
  frequency: z.string(),
  startedOn: z.string().nullable().optional(),
});

// ── Full patient response ─────────────────────────────────────────────────

export const PatientSchema = z.object({
  id: z.string(),
  fhirId: z.string().nullable().optional(),
  name: z.string(),
  age: z.number(),
  dob: z.string().nullable().optional(),
  gender: z.string().nullable().optional(),
  phone: z.string().nullable().optional(),
  email: z.string().nullable().optional(),
  conditions: z.array(z.string()).default([]),
  riskLevel: z.string(),
  status: z.string(),
  twinId: z.string().nullable().optional(),
  twinStatus: z.string(),
  twinCompleteness: z.number(),
  consentId: z.string().nullable().optional(),
  fhirConnected: z.boolean(),
  consentComplete: z.boolean(),
  wearableStatus: z.string(),
  providerName: z.string().nullable().optional(),
  providerId: z.string().nullable().optional(),
  ehrSystem: z.string().nullable().optional(),
  healthStatus: z.string().nullable().optional(),
  adherence: z.number(),
  createdAt: z.string().nullable().optional(),
  updatedAt: z.string().nullable().optional(),
  vitals: VitalSnapshotSchema,
  medications: z.array(MedicationSchema).nullable().optional(),
});
export type Patient = z.infer<typeof PatientSchema>;

// ── Paginated response ────────────────────────────────────────────────────

export const PageResponseSchema = <T extends z.ZodTypeAny>(itemSchema: T) =>
  z.object({
    content: z.array(itemSchema),
    totalElements: z.number(),
    totalPages: z.number(),
    page: z.number(),
    size: z.number(),
    first: z.boolean(),
    last: z.boolean(),
  });

export type PageResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  first: boolean;
  last: boolean;
};

// ── Filter options ────────────────────────────────────────────────────────

export const FilterOptionsSchema = z.object({
  conditions: z.array(z.string()),
  providers: z.array(z.string()),
  statuses: z.array(z.string()),
  riskLevels: z.array(z.string()),
});
export type FilterOptions = z.infer<typeof FilterOptionsSchema>;

// ── Create patient request (for form validation) ──────────────────────────

export const CreatePatientFormSchema = z.object({
  name: z.string().min(1, 'Full name is required').max(200),
  dob: z.string().min(1, 'Date of birth is required'),
  gender: z.string().min(1, 'Gender is required'),
  phone: z.string().optional(),
  email: z.string().email().optional().or(z.literal('')),
  fhirId: z.string().min(1, 'FHIR Patient ID is required'),
  ehrSystem: z.string().optional(),
  conditions: z.array(z.string()).optional(),
  consents: z.object({
    ehr: z.boolean(),
    wearable: z.boolean(),
    ai: z.boolean(),
  }).optional(),
});
export type CreatePatientForm = z.infer<typeof CreatePatientFormSchema>;
