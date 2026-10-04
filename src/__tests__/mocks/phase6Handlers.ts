import { http, HttpResponse } from 'msw';

const BASE = 'http://localhost:8080';

// ── Mock data ─────────────────────────────────────────────────────────────

export const mockRecommendations = [
  {
    id: 'rec-1',
    title: 'Medication Management',
    goal: 'Improve glycaemic control toward HbA1c < 7.0%',
    intervention: 'Consider titrating Metformin to 1000 mg BID.',
    monitoring: 'HbA1c at 12 weeks; fasting glucose weekly.',
    outcome: 'Projected HbA1c reduction of 0.8–1.2 percentage points.',
    evidence: 'ADA Standards of Care 2026 · Section 9',
  },
  {
    id: 'rec-2',
    title: 'Lifestyle Intervention',
    goal: 'Reduce cardiovascular risk through physical activity',
    intervention: '150 minutes moderate aerobic exercise per week.',
    monitoring: 'Activity tracker review monthly.',
    outcome: 'Expected 5–7% reduction in CVD risk.',
    evidence: 'ACC/AHA Prevention Guideline 2019',
  },
];

export const mockSafetyChecks = [
  { id: 'sc-1', label: 'Drug interaction validation', detail: 'No major interactions found.', tone: 'healthy' },
  { id: 'sc-2', label: 'Renal dose adjustment',       detail: 'Renal function within acceptable range.', tone: 'healthy' },
  { id: 'sc-3', label: 'Allergy validation',           detail: 'No documented allergies conflict.', tone: 'healthy' },
  { id: 'sc-4', label: 'Hypoglycaemia risk',           detail: 'Monitor blood glucose with dose titration.', tone: 'warning' },
];

export const mockDraftPlan = {
  id: 'CP-001',
  patientId: 'P001',
  patientName: 'John Doe',
  goal: 'HbA1c < 7.0%',
  riskLevel: 'High',
  adherence: 0,
  status: 'DRAFT' as const,
  providerId: 'PROV-001',
  generatedBy: 'AI',
  recommendations: mockRecommendations,
  safetyChecks: mockSafetyChecks,
  predictedOutcome: { metric: 'CVD risk', before: 24.3, after: 16.2, unit: '%' },
  approvedBy: null,
  approvedByName: null,
  approvedAt: null,
  approvalNotes: null,
  rejectionReason: null,
  createdAt: '2026-09-21T11:58:00Z',
  updatedAt: '2026-09-21T11:58:00Z',
};

export const mockActivePlan = {
  ...mockDraftPlan,
  id: 'CP-002',
  status: 'ACTIVE' as const,
  adherence: 78,
  approvedBy: 'PROV-001',
  approvedByName: 'Dr. A. Mehta',
  approvedAt: '2026-09-22T09:00:00Z',
};

export const mockRejectedPlan = {
  ...mockDraftPlan,
  id: 'CP-003',
  status: 'REJECTED' as const,
  rejectionReason: 'Needs revision',
};

export const mockCarePlanPage = {
  content: [mockDraftPlan, mockActivePlan],
  totalElements: 2,
  totalPages: 1,
  page: 0,
  size: 20,
  first: true,
  last: true,
};

export const mockCarePlanStats = {
  activeCount: 10,
  avgAdherence: 78.0,
  hospitalizationReduction: 23.0,
  draftCount: 3,
  rejectedCount: 2,
};

export const mockAdherence = {
  planId: 'CP-002',
  overall: 78,
  breakdown: [
    { label: 'Medication', value: 80 },
    { label: 'Monitoring', value: 90 },
    { label: 'Follow-up',  value: 70 },
  ],
};

export const mockAdherenceTrend = [
  { t: 'Week 1', value: 58 },
  { t: 'Week 2', value: 64 },
  { t: 'Week 3', value: 69 },
  { t: 'Week 4', value: 72 },
  { t: 'Week 5', value: 75 },
  { t: 'Week 6', value: 78 },
];

export const mockOutcomes = [
  {
    id: 'OUT-1',
    planId: 'CP-002',
    metric: 'HbA1c',
    baseline: 8.2,
    current: 7.6,
    goal: 7.0,
    unit: '%',
    trend: 'improving',
    updatedAt: '2026-09-22T12:00:00Z',
  },
];

export const mockCarePlanTimeline = [
  { id: 'cpt-1', timestamp: '2026-09-21T11:58:00Z', title: 'Care plan generated',  detail: 'AI generated.', tone: 'info' },
  { id: 'cpt-2', timestamp: '2026-09-22T09:00:00Z', title: 'Care plan approved',   detail: 'Approved by Dr. A. Mehta.', tone: 'healthy' },
];

// ── MSW Handlers ──────────────────────────────────────────────────────────

export const phase6Handlers = [

  // Generate care plan
  http.post(`${BASE}/api/care-plans/generate`, () =>
    HttpResponse.json(mockDraftPlan),
  ),

  // Care plans list
  http.get(`${BASE}/api/care-plans`, () =>
    HttpResponse.json(mockCarePlanPage),
  ),

  // Care plan stats
  http.get(`${BASE}/api/care-plans/stats`, () =>
    HttpResponse.json(mockCarePlanStats),
  ),

  // Single care plan — CP-001 = draft, CP-002 = active, CP-003 = rejected
  http.get(`${BASE}/api/care-plans/:planId`, ({ params }) => {
    if (params.planId === 'CP-001') return HttpResponse.json(mockDraftPlan);
    if (params.planId === 'CP-002') return HttpResponse.json(mockActivePlan);
    if (params.planId === 'CP-003') return HttpResponse.json(mockRejectedPlan);
    return HttpResponse.json({ message: 'Not found' }, { status: 404 });
  }),

  // Update care plan
  http.put(`${BASE}/api/care-plans/:planId`, ({ params }) =>
    HttpResponse.json({ ...mockDraftPlan, id: params.planId as string }),
  ),

  // Approve
  http.post(`${BASE}/api/care-plans/:planId/approve`, ({ params }) =>
    HttpResponse.json({
      ...mockDraftPlan,
      id: params.planId as string,
      status: 'ACTIVE',
      approvedBy: 'PROV-001',
      approvedByName: 'Dr. A. Mehta',
      approvedAt: new Date().toISOString(),
    }),
  ),

  // Reject
  http.post(`${BASE}/api/care-plans/:planId/reject`, ({ params }) =>
    HttpResponse.json({
      ...mockDraftPlan,
      id: params.planId as string,
      status: 'REJECTED',
      rejectionReason: 'Needs revision',
    }),
  ),

  // Timeline
  http.get(`${BASE}/api/care-plans/:planId/timeline`, () =>
    HttpResponse.json(mockCarePlanTimeline),
  ),

  // Adherence
  http.get(`${BASE}/api/care-plans/:planId/adherence`, () =>
    HttpResponse.json(mockAdherence),
  ),

  // Adherence trend
  http.get(`${BASE}/api/care-plans/:planId/adherence/trend`, () =>
    HttpResponse.json(mockAdherenceTrend),
  ),

  // Outcomes
  http.get(`${BASE}/api/care-plans/:planId/outcomes`, () =>
    HttpResponse.json(mockOutcomes),
  ),

  // Active care plan for patient
  http.get(`${BASE}/api/patients/:patientId/care-plan`, ({ params }) => {
    if (params.patientId === 'P001') return HttpResponse.json(mockActivePlan);
    return HttpResponse.json({ message: 'No active care plan' }, { status: 404 });
  }),
];
