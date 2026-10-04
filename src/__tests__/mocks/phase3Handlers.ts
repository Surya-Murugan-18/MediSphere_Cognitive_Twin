import { http, HttpResponse } from 'msw';

const BASE = 'http://localhost:8080';

// ── Mock vitals snapshot ──────────────────────────────────────────────────

export const mockVitalsSnapshot = {
  patientId: 'P001',
  heartRate: 85,
  bloodPressure: '120/80',
  spo2: 98.0,
  temperature: 36.8,
  respiratoryRate: 16,
  source: 'wearable',
  deviceId: 'DEV-P001',
  updatedAt: new Date().toISOString(),
};

// ── Mock vitals history ───────────────────────────────────────────────────

export const mockHeartRateHistory = {
  patientId: 'P001', type: 'heartRate', period: '24h',
  data: [
    { t: '08:00', value: 72, timestamp: new Date().toISOString() },
    { t: '12:00', value: 80, timestamp: new Date().toISOString() },
    { t: '16:00', value: 85, timestamp: new Date().toISOString() },
    { t: '20:00', value: 78, timestamp: new Date().toISOString() },
    { t: 'Now',   value: 85, timestamp: new Date().toISOString() },
  ],
};

export const mockBpSysHistory = {
  patientId: 'P001', type: 'bloodPressureSystolic', period: '7d',
  data: [
    { t: 'Mon', value: 120, timestamp: new Date().toISOString() },
    { t: 'Tue', value: 125, timestamp: new Date().toISOString() },
    { t: 'Wed', value: 122, timestamp: new Date().toISOString() },
  ],
};

export const mockBpDiaHistory = {
  patientId: 'P001', type: 'bloodPressureDiastolic', period: '7d',
  data: [
    { t: 'Mon', value: 78, timestamp: new Date().toISOString() },
    { t: 'Tue', value: 80, timestamp: new Date().toISOString() },
    { t: 'Wed', value: 79, timestamp: new Date().toISOString() },
  ],
};

export const mockSpo2History = {
  patientId: 'P001', type: 'spo2', period: '24h',
  data: [
    { t: '08:00', value: 98, timestamp: new Date().toISOString() },
    { t: '12:00', value: 97, timestamp: new Date().toISOString() },
    { t: 'Now',   value: 98, timestamp: new Date().toISOString() },
  ],
};

// ── Mock wearable device ──────────────────────────────────────────────────

export const mockWearableDevice = {
  id: 'DEV-P001', patientId: 'P001',
  displayName: 'Smart Watch · SW-1044',
  deviceType: 'Smartwatch', manufacturer: 'BioSense',
  kafkaTopic: 'vitals.raw', deviceKey: 'SW-1044',
  status: 'Online',
  lastSeen: new Date().toISOString(),
  registeredAt: new Date().toISOString(),
};

// ── Mock lab results ──────────────────────────────────────────────────────

export const mockLabResults = [
  {
    id: 'LAB-OBS-P001-hba1c', patientId: 'P001',
    loinc: '4548-4', test: 'HbA1c',
    result: '8.2 %', numeric: 8.2, unit: '%',
    referenceRange: '< 5.7%', status: 'High' as const,
    category: 'Metabolic' as const, date: '2026-09-06',
    trend: 'up' as const, previous: '7.9 %',
    significance: 'HbA1c is above the reference range. Clinical review recommended.',
  },
  {
    id: 'LAB-OBS-P001-glucose', patientId: 'P001',
    loinc: '2345-7', test: 'Fasting Glucose',
    result: '126 mg/dL', numeric: 126, unit: 'mg/dL',
    referenceRange: '70 – 100 mg/dL', status: 'High' as const,
    category: 'Metabolic' as const, date: '2026-09-06',
    trend: 'flat' as const, previous: '',
    significance: 'Fasting Glucose is above the reference range.',
  },
  {
    id: 'LAB-OBS-P001-cholesterol', patientId: 'P001',
    loinc: '2093-3', test: 'Total Cholesterol',
    result: '224 mg/dL', numeric: 224, unit: 'mg/dL',
    referenceRange: '< 200 mg/dL', status: 'High' as const,
    category: 'Lipids' as const, date: '2026-09-06',
    trend: 'down' as const, previous: '238 mg/dL',
    significance: 'Total Cholesterol is above the reference range.',
  },
];

export const mockLabPageResponse = {
  content: mockLabResults,
  totalElements: 3, totalPages: 1, page: 0, size: 50, first: true, last: true,
};

// ── MSW handlers ──────────────────────────────────────────────────────────

export const phase3Handlers = [
  // Vitals current
  http.get(`${BASE}/api/patients/:patientId/vitals/current`, ({ params }) =>
    HttpResponse.json({ ...mockVitalsSnapshot, patientId: params.patientId as string })),

  // Vitals history — dynamic type routing
  http.get(`${BASE}/api/patients/:patientId/vitals/history`, ({ request, params }) => {
    const url   = new URL(request.url);
    const type  = url.searchParams.get('type')   ?? 'heartRate';
    const period = url.searchParams.get('period') ?? '24h';
    const pid   = params.patientId as string;

    if (type === 'heartRate')              return HttpResponse.json({ ...mockHeartRateHistory, patientId: pid });
    if (type === 'bloodPressureSystolic')  return HttpResponse.json({ ...mockBpSysHistory, patientId: pid });
    if (type === 'bloodPressureDiastolic') return HttpResponse.json({ ...mockBpDiaHistory, patientId: pid });
    if (type === 'spo2')                   return HttpResponse.json({ ...mockSpo2History, patientId: pid });

    return HttpResponse.json({ patientId: pid, type, period, data: [] });
  }),

  // Wearable device
  http.get(`${BASE}/api/devices/by-patient/:patientId`, ({ params }) =>
    HttpResponse.json({ ...mockWearableDevice, patientId: params.patientId as string })),

  // Lab results paginated
  http.get(`${BASE}/api/patients/:patientId/labs`, ({ params }) =>
    HttpResponse.json({ ...mockLabPageResponse, content: mockLabResults.map(l => ({ ...l, patientId: params.patientId as string })) })),

  // Lab results recent
  http.get(`${BASE}/api/patients/:patientId/labs/recent`, () =>
    HttpResponse.json(mockLabResults.slice(0, 3))),
];
