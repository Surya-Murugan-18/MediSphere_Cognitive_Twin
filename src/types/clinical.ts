export type RiskLevel = 'High' | 'Medium' | 'Low';

export type StatusTone = 'healthy' | 'warning' | 'critical' | 'info' | 'neutral';

export type TwinStatus = 'Synchronized' | 'Syncing' | 'Not Created' | 'Stale';

export interface VitalSnapshot {
  heartRate: number;
  bloodPressure: string;
  spo2: number;
  temperature: number;
  respiratoryRate: number;
}

export interface Medication {
  name: string;
  dose: string;
  frequency: string;
  startedOn: string;
}

export interface LabResult {
  id: string;
  patientId: string;
  test: string;
  result: string;
  numeric: number;
  referenceRange: string;
  status: 'High' | 'Low' | 'Normal' | 'Pending';
  date: string;
  trend: 'up' | 'down' | 'flat';
  previous: string;
  significance: string;
  category: 'Metabolic' | 'Lipids' | 'Hematology' | 'Cardiac';
}

export interface TimelineEvent {
  id: string;
  timestamp: string;
  title: string;
  detail: string;
  tone: StatusTone;
}

export interface Patient {
  id: string;
  fhirId: string;
  name: string;
  age: number;
  gender: 'Male' | 'Female';
  dob: string;
  contact: string;
  conditions: string[];
  risk: RiskLevel;
  twinStatus: TwinStatus;
  twinId: string;
  twinCompleteness: number;
  lastUpdated: string;
  provider: string;
  status: 'Active' | 'Inactive' | 'Pending Consent';
  fhirConnected: boolean;
  consentComplete: boolean;
  wearable: 'Online' | 'Offline';
  vitals: VitalSnapshot;
  medications: Medication[];
  healthStatus: string;
  adherence: number;
}

export interface Alert {
  id: string;
  severity: 'HIGH' | 'MEDIUM' | 'LOW';
  patientId: string;
  patientName: string;
  event: string;
  analysis: string;
  type: string;
  time: string;
  detectedAt: string;
  status: 'Unacknowledged' | 'Acknowledged' | 'Escalated' | 'Resolved';
  provider: string;
  currentValue: string;
  previousValue: string;
  confidence: number;
}

export interface Prediction {
  id: string;
  patientId: string;
  patientName: string;
  model: string;
  label: string;
  value: number;
  category: RiskLevel;
  federatedRound: number;
  confidence: number;
  calibration: 'Calibrated' | 'Recalibration Due';
  updated: string;
}

export interface ShapFactor {
  feature: string;
  contribution: number;
  value: string;
  direction: 'increases' | 'decreases';
}

export interface CarePlan {
  id: string;
  patientId: string;
  patientName: string;
  goal: string;
  risk: RiskLevel;
  adherence: number;
  status: 'Active' | 'Awaiting Approval' | 'Draft' | 'Completed';
  provider: string;
  lastUpdated: string;
}

export interface AuditEntry {
  id: string;
  timestamp: string;
  user: string;
  action: string;
  patient: string;
  module: string;
  status: 'Success' | 'Denied';
}

export interface ServiceStatus {
  name: string;
  state: string;
  tone: StatusTone;
  uptime: string;
  detail: string;
}