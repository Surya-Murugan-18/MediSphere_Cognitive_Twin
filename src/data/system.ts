import type { AuditEntry, ServiceStatus } from '../types/clinical';

export const services: ServiceStatus[] = [
{ name: 'FHIR API (R4)', state: 'Connected', tone: 'healthy', uptime: '99.98%', detail: 'SMART on FHIR authorization active' },
{ name: 'MongoDB — Twin Store', state: 'Connected', tone: 'healthy', uptime: '99.99%', detail: '1,247 twin documents' },
{ name: 'Apache Kafka', state: 'Streaming', tone: 'healthy', uptime: '99.95%', detail: 'topics: vitals.raw, vitals.anomaly' },
{ name: 'TensorFlow Federated', state: 'Active', tone: 'healthy', uptime: '99.82%', detail: 'Round 47 completed' },
{ name: 'Wearable Gateway', state: '892 Online · 14 Offline', tone: 'warning', uptime: '98.10%', detail: '14 devices have not reported in over 30 minutes' },
{ name: 'FHIR Sync Worker', state: 'Active', tone: 'healthy', uptime: '99.91%', detail: 'Last bundle processed 40 s ago' },
{ name: 'Audit Logging', state: 'Active', tone: 'healthy', uptime: '100%', detail: 'Immutable write-ahead log' }];


export const systemEvents = [
{ id: 'se-1', time: '12:16:04', text: 'FHIR bundle ingested — 128 Observation resources', tone: 'neutral' as const },
{ id: 'se-2', time: '12:14:23', text: 'Anomaly detected on vitals.raw — alert A-2291 created', tone: 'critical' as const },
{ id: 'se-3', time: '11:34:10', text: 'Wearable gateway: 14 devices marked offline', tone: 'warning' as const },
{ id: 'se-4', time: '11:02:55', text: 'Federated round 47 aggregation completed', tone: 'healthy' as const }];


export const kafkaEvents = [
{ id: 'k1', time: '12:16:41', topic: 'vitals.raw', text: 'P002 · heart_rate=145 · device=SW-2291' },
{ id: 'k2', time: '12:16:39', topic: 'vitals.raw', text: 'P001 · spo2=98 · device=SW-1044' },
{ id: 'k3', time: '12:16:36', topic: 'vitals.anomaly', text: 'P002 · rule=HR_SPIKE_P95 · score=0.89' },
{ id: 'k4', time: '12:16:33', topic: 'vitals.raw', text: 'P003 · heart_rate=91 · device=SW-3120' },
{ id: 'k5', time: '12:16:30', topic: 'twin.update', text: 'HT-001 · state_version=4182' }];


export const auditEntries: AuditEntry[] = [
{ id: 'AU-8841', timestamp: '12:14:23', user: 'Dr. A. Mehta', action: 'Viewed Digital Twin', patient: 'John Doe', module: 'Health Twins', status: 'Success' },
{ id: 'AU-8842', timestamp: '12:15:02', user: 'Dr. A. Mehta', action: 'Reviewed Risk Prediction', patient: 'John Doe', module: 'Predictions', status: 'Success' },
{ id: 'AU-8843', timestamp: '12:17:42', user: 'Dr. A. Mehta', action: 'Approved Care Plan', patient: 'John Doe', module: 'Care Plans', status: 'Success' },
{ id: 'AU-8844', timestamp: '12:19:10', user: 'N. Alvarez, RN', action: 'Acknowledged Alert A-2290', patient: 'John Doe', module: 'Alerts', status: 'Success' },
{ id: 'AU-8845', timestamp: '12:21:36', user: 'J. Park (Analyst)', action: 'Attempted Export of Patient Record', patient: 'Sarah M.', module: 'Reports', status: 'Denied' },
{ id: 'AU-8846', timestamp: '12:24:07', user: 'Dr. L. Okafor', action: 'Updated Consent — Wearable Data', patient: 'Priya K.', module: 'Consent', status: 'Success' },
{ id: 'AU-8847', timestamp: '12:28:55', user: 'system', action: 'Federated Round 47 Aggregation', patient: '—', module: 'Federated Learning', status: 'Success' }];


export const reportCards = [
{ id: 'r1', title: 'Patient Risk Report', detail: 'Cohort risk stratification with model provenance.', lastRun: 'Today · 07:15', rows: '1,247 patients', ready: true },
{ id: 'r2', title: 'Digital Twin Report', detail: 'Twin completeness, sync status and data-source coverage.', lastRun: 'Today · 06:40', rows: '1,224 twins', ready: true },
{ id: 'r3', title: 'Alert Report', detail: 'Alert volume, severity mix and response times.', lastRun: 'Today · 07:15', rows: '47 alerts today', ready: true },
{ id: 'r4', title: 'Care Plan Report', detail: 'Plan status, approvals and adherence distribution.', lastRun: 'Yesterday · 19:02', rows: '1,124 plans', ready: true },
{ id: 'r5', title: 'Population Health Report', detail: 'Condition prevalence and risk trend by hospital.', lastRun: 'Sep 15 · 08:00', rows: '3 hospitals', ready: true },
{ id: 'r6', title: 'AI Model Report', detail: 'Federated rounds, accuracy and calibration audit.', lastRun: 'Not yet generated', rows: 'No data', ready: false }];


export const consentHistory = [
{ id: 'ch-1', date: '2026-09-12 11:24', type: 'EHR Data Access', status: 'Granted', updatedBy: 'Patient portal' },
{ id: 'ch-2', date: '2026-09-12 11:24', type: 'Wearable Data Access', status: 'Granted', updatedBy: 'Patient portal' },
{ id: 'ch-3', date: '2026-09-12 11:25', type: 'AI Risk Analysis', status: 'Granted', updatedBy: 'Dr. A. Mehta' },
{ id: 'ch-4', date: '2026-06-02 09:10', type: 'Research Data Sharing', status: 'Declined', updatedBy: 'Patient portal' }];


export const populationRiskTrend = [
{ t: 'Apr', high: 31, medium: 402, low: 760 },
{ t: 'May', high: 29, medium: 408, low: 772 },
{ t: 'Jun', high: 27, medium: 410, low: 784 },
{ t: 'Jul', high: 26, medium: 415, low: 795 },
{ t: 'Aug', high: 25, medium: 413, low: 804 },
{ t: 'Sep', high: 23, medium: 412, low: 812 }];


export const conditionDistribution = [
{ name: 'Diabetes', value: 486 },
{ name: 'Hypertension', value: 412 },
{ name: 'Cardiac', value: 198 },
{ name: 'COPD', value: 96 },
{ name: 'Heart Failure', value: 55 }];


export const alertTrend = [
{ t: 'Mon', alerts: 38, critical: 4 },
{ t: 'Tue', alerts: 44, critical: 6 },
{ t: 'Wed', alerts: 41, critical: 3 },
{ t: 'Thu', alerts: 52, critical: 7 },
{ t: 'Fri', alerts: 46, critical: 5 },
{ t: 'Sat', alerts: 33, critical: 2 },
{ t: 'Sun', alerts: 47, critical: 5 }];


export const hospitalOverview = [
{ name: 'Hospital A — Northside General', patients: 512, highRisk: 11, adherence: 81, alerts: 21 },
{ name: 'Hospital B — Lakeview Medical', patients: 438, highRisk: 8, adherence: 76, alerts: 17 },
{ name: 'Hospital C — Riverbend Clinic', patients: 297, highRisk: 4, adherence: 74, alerts: 9 }];


export const populationCategories = [
{ name: 'Cardiovascular', cohort: 623, highRisk: 14, trend: '-2.1%', tone: 'healthy' as const },
{ name: 'Diabetes', cohort: 486, highRisk: 7, trend: '-0.8%', tone: 'healthy' as const },
{ name: 'Readmission', cohort: 138, highRisk: 2, trend: '+0.4%', tone: 'warning' as const }];


export const recentActivity = [
{ id: 'ra-1', text: 'Risk reviewed — John Doe (CVD 24.3%)', actor: 'Dr. A. Mehta', time: '4 min ago', tone: 'info' as const },
{ id: 'ra-2', text: 'Alert acknowledged — Elevated BP, John Doe', actor: 'N. Alvarez, RN', time: '12 min ago', tone: 'warning' as const },
{ id: 'ra-3', text: 'Care plan approved — Priya K., HbA1c < 6.5%', actor: 'Dr. L. Okafor', time: '38 min ago', tone: 'healthy' as const },
{ id: 'ra-4', text: 'Patient twin updated — HT-006 resynchronized', actor: 'System', time: '1 h ago', tone: 'neutral' as const }];