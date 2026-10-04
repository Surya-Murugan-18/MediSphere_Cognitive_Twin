import { http, HttpResponse } from 'msw';

const BASE = 'http://localhost:8080';

// ── Mock data ─────────────────────────────────────────────────────────────

export const mockConsent = {
  id: 'CON-001',
  patientId: 'P001',
  ehr: true,
  wearable: true,
  ai: true,
  updatedAt: '2026-09-21T12:00:00Z',
  updatedBy: 'Dr. A. Mehta',
};

export const mockConsentHistory = [
  { id: 'ch-1', date: '2026-09-21T12:00:00Z', type: 'EHR Data Access',    status: 'Granted',  updatedBy: 'Dr. A. Mehta' },
  { id: 'ch-2', date: '2026-09-12T11:24:00Z', type: 'AI Risk Analysis',   status: 'Granted',  updatedBy: 'Patient portal' },
  { id: 'ch-3', date: '2026-06-02T09:00:00Z', type: 'Wearable Data Access', status: 'Declined', updatedBy: 'Patient portal' },
];

export const mockAuditPage = {
  content: [
    {
      id: 'AU-001',
      timestamp: '2026-09-21T12:14:23Z',
      userId: 'PROV-001',
      userName: 'Dr. A. Mehta',
      userRole: 'CLINICIAN',
      action: 'Viewed Digital Twin',
      patientId: 'P001',
      patientName: 'John Doe',
      module: 'Health Twins',
      status: 'Success',
      ipAddress: '10.0.0.45',
    },
    {
      id: 'AU-002',
      timestamp: '2026-09-21T12:17:42Z',
      userId: 'PROV-001',
      userName: 'Dr. A. Mehta',
      userRole: 'CLINICIAN',
      action: 'Approved Care Plan',
      patientId: 'P001',
      patientName: 'John Doe',
      module: 'Care Plans',
      status: 'Success',
      ipAddress: '10.0.0.45',
    },
  ],
  totalElements: 2,
  totalPages: 1,
  page: 0,
  size: 20,
};

export const mockDashboardStats = {
  patientsOnboarded: 1247,
  fhirResources: 24000,
  activeAlerts: 3,
  wearablesOnline: 892,
  highRiskPatients: 23,
  activePlans: 1124,
  avgAdherence: 78.0,
  avgAlertResponseMin: 3.2,
  wearablesOffline: 14,
  plansAwaitingApproval: 12,
};

export const mockRiskDistribution = [
  { name: 'Low',    value: 812, tone: '#0e9f7e' },
  { name: 'Medium', value: 412, tone: '#d98a00' },
  { name: 'High',   value: 23,  tone: '#d13f3f' },
];

export const mockPopulationStats = {
  totalPatients: 1247,
  highRiskCount: 23,
  mediumRiskCount: 412,
  lowRiskCount: 812,
  avgAdherence: 78.0,
  activeAlerts: 47,
  activePlans: 1124,
  hospitalizationReduction: '12.4%',
};

export const mockSearchResults = [
  {
    category: 'Patients',
    results: [
      { id: 'P001', label: 'John Doe',  detail: 'P001 · fhir:Patient/8a21-4c77', to: '/patients/P001' },
      { id: 'P002', label: 'Sarah M.',  detail: 'P002 · fhir:Patient/b932-1122', to: '/patients/P002' },
    ],
  },
  {
    category: 'Alerts',
    results: [
      { id: 'A-2291', label: 'Heart Rate Spike: 145 BPM', detail: 'Sarah M. · HIGH', to: '/alerts/A-2291' },
    ],
  },
];

export const mockReportCards = [
  { id: 'r1', title: 'Patient Risk Report',   detail: 'Cohort risk stratification.', lastRun: 'Today · 07:15', rows: '1,247 patients', ready: true,  lastRunAt: '2026-09-21T07:15:00Z' },
  { id: 'r2', title: 'Digital Twin Report',   detail: 'Twin completeness.',          lastRun: 'Today · 06:40', rows: '1,224 twins',   ready: true,  lastRunAt: '2026-09-21T06:40:00Z' },
  { id: 'r6', title: 'AI Model Report',       detail: 'Federated rounds.',           lastRun: 'Not yet generated', rows: 'No data',   ready: false, lastRunAt: null },
];

export const mockSystemServices = [
  { name: 'MongoDB — Twin Store', state: 'Connected',  uptime: '99.99%', detail: '5 twin documents', tone: 'healthy' },
  { name: 'Apache Kafka',         state: 'Streaming',  uptime: '99.95%', detail: 'topics: vitals.raw', tone: 'healthy' },
  { name: 'FHIR API (R4)',        state: 'Connected',  uptime: '99.98%', detail: 'Mock mode active', tone: 'healthy' },
  { name: 'TensorFlow Federated', state: 'Active',     uptime: '99.82%', detail: 'Stub active', tone: 'healthy' },
  { name: 'Wearable Gateway',     state: '892 Online · 14 Offline', uptime: '98.10%', detail: '14 devices offline', tone: 'warning' },
  { name: 'FHIR Sync Worker',     state: 'Idle',       uptime: '99.91%', detail: 'No sync events yet', tone: 'neutral' },
  { name: 'Audit Logging',        state: 'Active',     uptime: '100%',   detail: 'Append-only log', tone: 'healthy' },
];

export const mockSystemEvents = [
  { id: 'SE-001', timestamp: '2026-09-21T12:16:04Z', text: 'FHIR bundle ingested — 128 observations', tone: 'neutral',  category: 'FHIR' },
  { id: 'SE-002', timestamp: '2026-09-21T12:14:23Z', text: 'Anomaly detected — alert A-2291 created', tone: 'critical', category: 'Anomaly' },
];

export const mockProviderProfile = {
  id: 'PROV-001',
  name: 'Dr. A. Mehta',
  email: 'a.mehta@medisphere.dev',
  role: 'CLINICIAN' as const,
  specialty: 'Cardiology',
  facility: 'Hospital A — Northside General',
  npi: '1234567890',
  notificationPrefs: { critical: true, risk: true, approvals: true, system: false },
  lastSignIn: '2026-09-21T07:02:00Z',
  active: true,
};

export const mockFhirConfig = {
  mode: 'mock',
  baseUrl: '',
  version: 'R4',
  authType: 'SMART on FHIR',
  syncIntervalMinutes: 5,
  updatedBy: null,
  updatedAt: null,
};

// ── MSW Handlers ──────────────────────────────────────────────────────────

export const phase7Handlers = [

  // Consent
  http.get(`${BASE}/api/patients/:patientId/consent`, () =>
    HttpResponse.json(mockConsent),
  ),
  http.put(`${BASE}/api/patients/:patientId/consent`, async ({ request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json({ ...mockConsent, ...body });
  }),
  http.get(`${BASE}/api/patients/:patientId/consent/history`, () =>
    HttpResponse.json(mockConsentHistory),
  ),

  // Audit
  http.get(`${BASE}/api/audit`, () =>
    HttpResponse.json(mockAuditPage),
  ),

  // Dashboard
  http.get(`${BASE}/api/dashboard/stats`, () =>
    HttpResponse.json(mockDashboardStats),
  ),
  http.get(`${BASE}/api/population/risk-distribution`, () =>
    HttpResponse.json(mockRiskDistribution),
  ),

  // Population
  http.get(`${BASE}/api/population/stats`, () =>
    HttpResponse.json(mockPopulationStats),
  ),
  http.get(`${BASE}/api/population/condition-distribution`, () =>
    HttpResponse.json([{ name: 'Diabetes', value: 486 }, { name: 'Hypertension', value: 412 }]),
  ),
  http.get(`${BASE}/api/population/hospitals`, () =>
    HttpResponse.json([{ name: 'Hospital A', patients: 512, highRisk: 11, adherence: 81, alerts: 21 }]),
  ),
  http.get(`${BASE}/api/population/alert-trend`, () =>
    HttpResponse.json([{ t: 'Mon', alerts: 38, critical: 4 }]),
  ),
  http.get(`${BASE}/api/population/categories`, () =>
    HttpResponse.json([{ name: 'Cardiovascular', cohort: 623, highRisk: 14, trend: '-2.1%', tone: 'healthy' }]),
  ),

  // Reports
  http.get(`${BASE}/api/reports`, () =>
    HttpResponse.json(mockReportCards),
  ),
  http.post(`${BASE}/api/reports/:reportId/generate`, ({ params }) =>
    HttpResponse.json({ jobId: 'JOB-ABC123', reportId: params.reportId }),
  ),

  // System
  http.get(`${BASE}/api/system/services`, () =>
    HttpResponse.json(mockSystemServices),
  ),
  http.get(`${BASE}/api/system/events`, () =>
    HttpResponse.json(mockSystemEvents),
  ),

  // Settings — provider
  http.get(`${BASE}/api/providers/me`, () =>
    HttpResponse.json(mockProviderProfile),
  ),
  http.put(`${BASE}/api/providers/me`, async ({ request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json({ ...mockProviderProfile, ...body });
  }),
  http.get(`${BASE}/api/providers/me/notifications`, () =>
    HttpResponse.json(mockProviderProfile.notificationPrefs),
  ),
  http.put(`${BASE}/api/providers/me/notifications`, async ({ request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json({ ...mockProviderProfile.notificationPrefs, ...body });
  }),

  // Settings — FHIR (ADMIN only)
  http.get(`${BASE}/api/settings/fhir`, () =>
    HttpResponse.json(mockFhirConfig),
  ),
  http.put(`${BASE}/api/settings/fhir`, async ({ request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json({ ...mockFhirConfig, ...body });
  }),

  // Search
  http.get(`${BASE}/api/search`, ({ request }) => {
    const q = new URL(request.url).searchParams.get('q') ?? '';
    if (q.length < 2) return HttpResponse.json([]);
    return HttpResponse.json(mockSearchResults);
  }),
];
