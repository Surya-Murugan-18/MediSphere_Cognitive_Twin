import api from './client';
import {
  PatientSchema,
  PatientSummarySchema,
  FilterOptionsSchema,
  PageResponseSchema,
  type Patient,
  type PatientSummary,
  type FilterOptions,
  type PageResponse,
} from '../schemas/patient.schema';
import { z } from 'zod';

// ── Filter params ─────────────────────────────────────────────────────────

export interface PatientFilters {
  search?:       string;
  riskLevel?:    string;
  condition?:    string;
  status?:       string;
  providerName?: string;
}

// ── List ──────────────────────────────────────────────────────────────────

export async function getPatients(
  filters: PatientFilters = {},
  page = 0,
  size = 20,
): Promise<PageResponse<PatientSummary>> {
  const params = new URLSearchParams();
  if (filters.search)       params.set('search',       filters.search);
  if (filters.riskLevel && filters.riskLevel !== 'All risk')
                             params.set('riskLevel',    filters.riskLevel);
  if (filters.condition && filters.condition !== 'All conditions')
                             params.set('condition',    filters.condition);
  if (filters.status && filters.status !== 'All statuses')
                             params.set('status',       filters.status);
  if (filters.providerName && filters.providerName !== 'All providers')
                             params.set('providerName', filters.providerName);
  params.set('page', String(page));
  params.set('size', String(size));

  const response = await api.get(`/api/patients?${params.toString()}`);
  return PageResponseSchema(PatientSummarySchema).parse(response.data);
}

// ── Single patient ────────────────────────────────────────────────────────

export async function getPatient(id: string): Promise<Patient> {
  const response = await api.get(`/api/patients/${id}`);
  return PatientSchema.parse(response.data);
}

// ── Create ────────────────────────────────────────────────────────────────

export interface CreatePatientPayload {
  name:        string;
  dob:         string;
  gender:      string;
  phone?:      string;
  email?:      string;
  fhirId:      string;
  ehrSystem?:  string;
  conditions?: string[];
  consents?:   { ehr: boolean; wearable: boolean; ai: boolean };
}

export async function createPatient(data: CreatePatientPayload): Promise<Patient> {
  const response = await api.post('/api/patients', data);
  return PatientSchema.parse(response.data);
}

// ── Update ────────────────────────────────────────────────────────────────

export interface UpdatePatientPayload {
  name?:         string;
  dob?:          string;
  gender?:       string;
  phone?:        string;
  email?:        string;
  fhirId?:       string;
  ehrSystem?:    string;
  conditions?:   string[];
  status?:       string;
  riskLevel?:    string;
  healthStatus?: string;
  adherence?:    number;
}

export async function updatePatient(id: string, data: UpdatePatientPayload): Promise<Patient> {
  const response = await api.put(`/api/patients/${id}`, data);
  return PatientSchema.parse(response.data);
}

// ── Next ID ───────────────────────────────────────────────────────────────

export async function getNextPatientId(): Promise<string> {
  const response = await api.get('/api/patients/next-id');
  return z.object({ nextId: z.string() }).parse(response.data).nextId;
}

// ── Filter options ────────────────────────────────────────────────────────

export async function getFilterOptions(): Promise<FilterOptions> {
  const response = await api.get('/api/patients/filter-options');
  return FilterOptionsSchema.parse(response.data);
}

// ── Timeline ──────────────────────────────────────────────────────────────

/**
 * API timeline event — uses nullable timestamp to match backend response.
 * Mapped to clinical.ts TimelineEvent (non-nullable) in page components.
 */
export interface ApiTimelineEvent {
  id:        string;
  timestamp: string | null | undefined;
  title:     string;
  detail:    string;
  tone:      string;
}

export async function getPatientTimeline(patientId: string): Promise<ApiTimelineEvent[]> {
  const response = await api.get(`/api/patients/${patientId}/timeline`);
  const schema = z.array(z.object({
    id:        z.string(),
    timestamp: z.string().nullable().optional(),
    title:     z.string(),
    detail:    z.string(),
    tone:      z.string(),
  }));
  return schema.parse(response.data) as ApiTimelineEvent[];
}

// ── Per-patient sub-resources stubs (wired in later phases) ──────────────

/** Phase 3: real lab results via getLabResults */
export { getRecentLabResults as getPatientLabs } from './labs';

export { getPatientPredictions } from './predictions';
export { getPatientAlerts } from './alerts';
export async function getPatientCarePlan(_patientId: string)    { return null; }
