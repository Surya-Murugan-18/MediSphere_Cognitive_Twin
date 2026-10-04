import type { Alert } from '../types/clinical';

export const alerts: Alert[] = [
{
  id: 'A-2291',
  severity: 'HIGH',
  patientId: 'P002',
  patientName: 'Sarah M.',
  event: 'Heart Rate Spike: 145 BPM',
  analysis: 'Possible abnormal condition detected — sustained tachycardia inconsistent with patient baseline.',
  type: 'Vitals anomaly',
  time: '2 min ago',
  detectedAt: '12:14:23',
  status: 'Unacknowledged',
  provider: 'Dr. A. Mehta',
  currentValue: '145 BPM',
  previousValue: '68 BPM',
  confidence: 89
},
{
  id: 'A-2290',
  severity: 'MEDIUM',
  patientId: 'P001',
  patientName: 'John Doe',
  event: 'Elevated Blood Pressure: 142/91',
  analysis: 'Possible abnormal condition detected — readings above target range for three consecutive measurements.',
  type: 'Vitals anomaly',
  time: '15 min ago',
  detectedAt: '12:01:08',
  status: 'Acknowledged',
  provider: 'Dr. A. Mehta',
  currentValue: '142/91 mmHg',
  previousValue: '124/79 mmHg',
  confidence: 76
},
{
  id: 'A-2289',
  severity: 'MEDIUM',
  patientId: 'P006',
  patientName: 'Daniel W.',
  event: 'SpO2 Decline: 93%',
  analysis: 'Possible abnormal condition detected — gradual desaturation over 6 hours.',
  type: 'Vitals anomaly',
  time: '41 min ago',
  detectedAt: '11:35:52',
  status: 'Acknowledged',
  provider: 'Dr. L. Okafor',
  currentValue: '93%',
  previousValue: '97%',
  confidence: 71
},
{
  id: 'A-2288',
  severity: 'LOW',
  patientId: 'P004',
  patientName: 'Marcus T.',
  event: 'Wearable device offline',
  analysis: 'Device has not streamed to Kafka topic vitals.raw for 42 minutes.',
  type: 'Device',
  time: '42 min ago',
  detectedAt: '11:34:10',
  status: 'Acknowledged',
  provider: 'Care team',
  currentValue: 'No signal',
  previousValue: 'Streaming',
  confidence: 99
},
{
  id: 'A-2287',
  severity: 'MEDIUM',
  patientId: 'P003',
  patientName: 'Priya K.',
  event: 'Glucose variability increase',
  analysis: 'Possible abnormal condition detected — post-prandial excursions trending upward.',
  type: 'Risk change',
  time: '1 h ago',
  detectedAt: '11:12:44',
  status: 'Resolved',
  provider: 'Dr. L. Okafor',
  currentValue: '188 mg/dL peak',
  previousValue: '162 mg/dL peak',
  confidence: 68
}];


export const alertAuditTrail = [
{ id: 'au-1', time: '12:14:23', actor: 'MediSphere Stream Processor', action: 'Anomaly detected on topic vitals.raw · rule HR_SPIKE_P95' },
{ id: 'au-2', time: '12:14:24', actor: 'Alert Service', action: 'Alert A-2291 created with severity HIGH' },
{ id: 'au-3', time: '12:14:25', actor: 'Notification Service', action: 'Routed to Dr. A. Mehta (on-call cardiology)' },
{ id: 'au-4', time: '12:14:31', actor: 'Dr. A. Mehta', action: 'Alert opened — awaiting clinician acknowledgement' }];


export const notifications = [
{
  category: 'Critical Alerts',
  tone: 'critical' as const,
  items: [
  { id: 'n1', title: 'Sarah M. — HR spike detected', detail: '145 BPM · unacknowledged', time: '2 min ago', to: '/alerts/A-2291' }]

},
{
  category: 'Risk Updates',
  tone: 'warning' as const,
  items: [
  { id: 'n2', title: 'John Doe CVD risk updated', detail: '22.7% → 24.3% (round 47)', time: '9 min ago', to: '/predictions' }]

},
{
  category: 'Care Plan Approvals',
  tone: 'info' as const,
  items: [
  { id: 'n3', title: 'Care plan awaiting approval', detail: 'John Doe · glycemic control plan', time: '14 min ago', to: '/care-plans/CP-001/review' }]

},
{
  category: 'System Notifications',
  tone: 'neutral' as const,
  items: [
  { id: 'n4', title: 'Federated round 47 completed', detail: 'Global model CVD-Risk-v3.2 published', time: '1 h ago', to: '/federated' }]

}];