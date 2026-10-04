import type { CarePlan } from '../types/clinical';

export const carePlans: CarePlan[] = [
{
  id: 'CP-001',
  patientId: 'P001',
  patientName: 'John Doe',
  goal: 'HbA1c < 7.0%',
  risk: 'High',
  adherence: 87,
  status: 'Awaiting Approval',
  provider: 'Dr. A. Mehta',
  lastUpdated: '14 min ago'
},
{
  id: 'CP-002',
  patientId: 'P002',
  patientName: 'Sarah M.',
  goal: 'Rate control, resting HR < 90',
  risk: 'High',
  adherence: 76,
  status: 'Active',
  provider: 'Dr. A. Mehta',
  lastUpdated: '1 h ago'
},
{
  id: 'CP-003',
  patientId: 'P003',
  patientName: 'Priya K.',
  goal: 'HbA1c < 6.5%',
  risk: 'Medium',
  adherence: 84,
  status: 'Active',
  provider: 'Dr. L. Okafor',
  lastUpdated: '3 h ago'
},
{
  id: 'CP-004',
  patientId: 'P006',
  patientName: 'Daniel W.',
  goal: 'Prevent 30-day readmission',
  risk: 'High',
  adherence: 71,
  status: 'Active',
  provider: 'Dr. A. Mehta',
  lastUpdated: '5 h ago'
},
{
  id: 'CP-005',
  patientId: 'P004',
  patientName: 'Marcus T.',
  goal: 'BP < 130/80',
  risk: 'Medium',
  adherence: 62,
  status: 'Draft',
  provider: 'Dr. L. Okafor',
  lastUpdated: 'Yesterday'
}];


export const carePlanRecommendations = [
{
  id: 'rec-1',
  title: 'Medication Management',
  goal: 'Improve glycemic control toward HbA1c < 7.0%',
  intervention: 'Consider titrating Metformin to 1000 mg BID and evaluate adding an SGLT2 inhibitor per institutional diabetes pathway.',
  monitoring: 'HbA1c at 12 weeks; renal panel at 4 weeks after any change.',
  outcome: 'Projected HbA1c reduction of 0.8 – 1.2 percentage points.',
  evidence: 'ADA Standards of Care 2026 · Section 9'
},
{
  id: 'rec-2',
  title: 'Blood Pressure Monitoring',
  goal: 'Sustain BP below 130/80 mmHg',
  intervention: 'Home BP monitoring twice daily; review Lisinopril dose at next visit if readings remain above target.',
  monitoring: 'Wearable + cuff readings streamed daily; weekly clinician review.',
  outcome: 'Reduced hypertensive burden contributing to CVD risk.',
  evidence: 'ACC/AHA Hypertension Guideline'
},
{
  id: 'rec-3',
  title: 'Glucose Monitoring',
  goal: 'Reduce post-prandial excursions',
  intervention: 'Continuous glucose monitoring for 14 days with structured logging of meals.',
  monitoring: 'CGM time-in-range reviewed at 2 and 6 weeks.',
  outcome: 'Increase time-in-range from 58% toward 70%.',
  evidence: 'ADA Standards of Care 2026 · Section 7'
},
{
  id: 'rec-4',
  title: 'Follow-up Evaluation',
  goal: 'Confirm response to intervention',
  intervention: 'Schedule clinician follow-up at 6 weeks with repeat lipid and metabolic panel.',
  monitoring: 'Adherence tracking + twin state comparison at follow-up.',
  outcome: 'Care-plan effectiveness confirmed or plan revised.',
  evidence: 'Institutional chronic-care pathway'
}];


export const safetyChecks = [
{ id: 'sc-1', label: 'Drug interaction validation', detail: 'No major interactions found across 3 active medications.', tone: 'healthy' as const },
{ id: 'sc-2', label: 'Renal dose check', detail: 'Creatinine 0.9 mg/dL — no dose adjustment required.', tone: 'healthy' as const },
{ id: 'sc-3', label: 'Allergy cross-check', detail: 'No documented allergies conflict with this plan.', tone: 'healthy' as const },
{ id: 'sc-4', label: 'Hypoglycemia risk', detail: 'Monitor closely if additional agent is added.', tone: 'warning' as const }];


export const adherenceBreakdown = [
{ label: 'Medication', value: 80 },
{ label: 'Monitoring', value: 90 },
{ label: 'Follow-up', value: 70 }];


export const adherenceTrend = [
{ t: 'Week 1', value: 58 },
{ t: 'Week 2', value: 64 },
{ t: 'Week 3', value: 69 },
{ t: 'Week 4', value: 72 },
{ t: 'Week 5', value: 75 },
{ t: 'Week 6', value: 78 }];


export const carePlanTimeline = [
{ id: 'cpt-1', timestamp: 'Sep 16 · 09:02', title: 'Care plan approved', detail: 'Dr. A. Mehta approved plan CP-001.', tone: 'healthy' as const },
{ id: 'cpt-2', timestamp: 'Sep 18 · 08:30', title: 'Intervention started', detail: 'Metformin titration and home BP monitoring initiated.', tone: 'info' as const },
{ id: 'cpt-3', timestamp: 'Sep 19 · 17:12', title: 'Adherence dip recorded', detail: 'Two missed evening doses logged by patient app.', tone: 'warning' as const },
{ id: 'cpt-4', timestamp: 'Today · 12:16', title: 'Outcome measurement', detail: 'HbA1c 7.6% recorded — trending toward goal.', tone: 'healthy' as const }];