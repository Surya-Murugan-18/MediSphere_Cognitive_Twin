import { http, HttpResponse } from 'msw';

const BASE = 'http://localhost:8080';

// ── Alert mock data ────────────────────────────────────────────────────────

export const mockAlertUnack = {
  id: 'A-2291',
  severity: 'HIGH' as const,
  patientId: 'P002',
  patientName: 'Sarah M.',
  event: 'Heart Rate Spike: 145 BPM',
  analysis: 'Possible atrial fibrillation — sustained tachycardia inconsistent with patient baseline.',
  type: 'Vitals anomaly',
  ruleCode: 'HR_SPIKE_P95',
  detectedAt: '2026-09-21T12:14:23Z',
  status: 'Unacknowledged' as const,
  assignedProvider: 'PROV-001',
  currentValue: '145 BPM',
  previousValue: '68 BPM',
  confidence: 89,
  auditTrail: [
    { id: 'at-1', timestamp: '2026-09-21T12:14:23Z', actor: 'MediSphere Stream Processor', actorId: 'system', action: 'Anomaly detected on vitals.raw · rule HR_SPIKE_P95' },
    { id: 'at-2', timestamp: '2026-09-21T12:14:24Z', actor: 'Alert Service',               actorId: 'system', action: 'Alert A-2291 created with severity HIGH' },
  ],
  createdAt: '2026-09-21T12:14:23Z',
};

export const mockAlertAcknowledged = {
  ...mockAlertUnack,
  id: 'A-2290',
  severity: 'MEDIUM' as const,
  patientName: 'John Doe',
  patientId: 'P001',
  event: 'Elevated Blood Pressure: 142/91',
  status: 'Acknowledged' as const,
  ruleCode: 'BP_ELEVATED',
  acknowledgedAt: '2026-09-21T12:16:00Z',
  acknowledgedBy: 'PROV-001',
  auditTrail: [
    { id: 'at-1', timestamp: '2026-09-21T12:01:08Z', actor: 'MediSphere Stream Processor', actorId: 'system', action: 'Anomaly detected · rule BP_ELEVATED' },
    { id: 'at-2', timestamp: '2026-09-21T12:01:09Z', actor: 'Alert Service',               actorId: 'system', action: 'Alert A-2290 created' },
    { id: 'at-3', timestamp: '2026-09-21T12:16:00Z', actor: 'PROV-001',                    actorId: 'PROV-001', action: 'Alert acknowledged' },
  ],
};

export const mockAlerts = [mockAlertUnack, mockAlertAcknowledged];

export const mockAlertPage = {
  content: mockAlerts,
  totalElements: 2,
  totalPages: 1,
  page: 0,
  size: 20,
  first: true,
  last: true,
};

export const mockAlertCount = { count: 1 };

// ── Monitoring mock data ───────────────────────────────────────────────────

export const mockMonitoringStats = {
  alertsToday:     5,
  wearablesOnline: 12,
  avgResponseMin:  3.2,
  streamStatus:    'Active',
};

export const mockKafkaStats = {
  eventsPerSec: 12.5,
  consumerLag:  '0 ms',
  latestEvents: [
    { id: 'k1', time: '12:16:41', topic: 'vitals.raw',    text: 'P002 · heart_rate=145' },
    { id: 'k2', time: '12:16:39', topic: 'vitals.raw',    text: 'P001 · spo2=98' },
    { id: 'k3', time: '12:16:36', topic: 'vitals.anomaly',text: 'P002 · rule=HR_SPIKE_P95' },
  ],
};

export const mockKafkaEvents = [
  { id: 'k1', time: '12:16:41', topic: 'vitals.raw',    text: 'P002 · heart_rate=145 · device=DEV-001' },
  { id: 'k2', time: '12:16:39', topic: 'vitals.raw',    text: 'P001 · spo2=98 · device=DEV-002' },
  { id: 'k3', time: '12:16:36', topic: 'vitals.anomaly',text: 'P002 · rule=HR_SPIKE_P95 · score=0.89' },
  { id: 'k4', time: '12:16:33', topic: 'vitals.raw',    text: 'P003 · heart_rate=91 · device=DEV-003' },
];

// ── MSW Handlers ──────────────────────────────────────────────────────────

export const phase5Handlers = [

  // Alerts list
  http.get(`${BASE}/api/alerts`, ({ request }) => {
    const url = new URL(request.url);
    const status = url.searchParams.get('status');
    const content = status === 'Unacknowledged'
      ? [mockAlertUnack]
      : mockAlerts;
    return HttpResponse.json({
      content,
      totalElements: content.length,
      totalPages: 1, page: 0, size: 20, first: true, last: true,
    });
  }),

  // Alert count
  http.get(`${BASE}/api/alerts/count`, () =>
    HttpResponse.json(mockAlertCount),
  ),

  // Single alert
  http.get(`${BASE}/api/alerts/:alertId`, ({ params }) => {
    const alert = mockAlerts.find(a => a.id === params.alertId);
    return alert ? HttpResponse.json(alert) : HttpResponse.json(null, { status: 404 });
  }),

  // Acknowledge
  http.patch(`${BASE}/api/alerts/:alertId/acknowledge`, ({ params }) => {
    const alert = mockAlerts.find(a => a.id === params.alertId) ?? mockAlertUnack;
    return HttpResponse.json({
      ...alert,
      status: 'Acknowledged',
      acknowledgedAt: new Date().toISOString(),
      acknowledgedBy: 'PROV-001',
      auditTrail: [...(alert.auditTrail ?? []),
        { id: 'at-new', timestamp: new Date().toISOString(), actor: 'PROV-001', actorId: 'PROV-001', action: 'Alert acknowledged' },
      ],
    });
  }),

  // Escalate
  http.post(`${BASE}/api/alerts/:alertId/escalate`, ({ params }) => {
    const alert = mockAlerts.find(a => a.id === params.alertId) ?? mockAlertUnack;
    return HttpResponse.json({ ...alert, status: 'Escalated', escalatedAt: new Date().toISOString() });
  }),

  // Resolve
  http.post(`${BASE}/api/alerts/:alertId/resolve`, ({ params }) => {
    const alert = mockAlerts.find(a => a.id === params.alertId) ?? mockAlertUnack;
    return HttpResponse.json({ ...alert, status: 'Resolved', resolvedAt: new Date().toISOString(), resolution: 'Patient stabilised' });
  }),

  // Patient alerts
  http.get(`${BASE}/api/patients/:patientId/alerts`, () =>
    HttpResponse.json([mockAlertAcknowledged]),
  ),

  // Monitoring stats
  http.get(`${BASE}/api/monitoring/stats`, () =>
    HttpResponse.json(mockMonitoringStats),
  ),

  // Kafka stats
  http.get(`${BASE}/api/monitoring/kafka-stats`, () =>
    HttpResponse.json(mockKafkaStats),
  ),

  // Kafka events
  http.get(`${BASE}/api/monitoring/kafka-events`, () =>
    HttpResponse.json(mockKafkaEvents),
  ),
];
