import type { Prediction, ShapFactor } from '../types/clinical';

export const predictions: Prediction[] = [
{
  id: 'PR-9001',
  patientId: 'P001',
  patientName: 'John Doe',
  model: 'CVD-Risk-v3.2',
  label: '10-Year Cardiovascular Risk',
  value: 24.3,
  category: 'High',
  federatedRound: 47,
  confidence: 91,
  calibration: 'Calibrated',
  updated: '9 min ago'
},
{
  id: 'PR-9002',
  patientId: 'P001',
  patientName: 'John Doe',
  model: 'DM-Complication-v2.4',
  label: 'Diabetes Complication Risk (12 mo)',
  value: 31.8,
  category: 'High',
  federatedRound: 47,
  confidence: 88,
  calibration: 'Calibrated',
  updated: '9 min ago'
},
{
  id: 'PR-9003',
  patientId: 'P001',
  patientName: 'John Doe',
  model: 'Readmit-30d-v1.8',
  label: '30-Day Readmission Risk',
  value: 12.6,
  category: 'Medium',
  federatedRound: 46,
  confidence: 84,
  calibration: 'Recalibration Due',
  updated: '2 h ago'
},
{
  id: 'PR-9010',
  patientId: 'P002',
  patientName: 'Sarah M.',
  model: 'CVD-Risk-v3.2',
  label: '10-Year Cardiovascular Risk',
  value: 19.4,
  category: 'High',
  federatedRound: 47,
  confidence: 87,
  calibration: 'Calibrated',
  updated: '12 min ago'
},
{
  id: 'PR-9020',
  patientId: 'P003',
  patientName: 'Priya K.',
  model: 'DM-Complication-v2.4',
  label: 'Diabetes Complication Risk (12 mo)',
  value: 14.1,
  category: 'Medium',
  federatedRound: 47,
  confidence: 90,
  calibration: 'Calibrated',
  updated: '26 min ago'
}];


export const shapFactors: ShapFactor[] = [
{ feature: 'HbA1c', contribution: 8, value: '8.2%', direction: 'increases' },
{ feature: 'Blood Pressure', contribution: 6, value: '142/91 mmHg', direction: 'increases' },
{ feature: 'Age', contribution: 4.2, value: '58 years', direction: 'increases' },
{ feature: 'Total Cholesterol', contribution: 3.1, value: '224 mg/dL', direction: 'increases' },
{ feature: 'Smoking History', contribution: 2.4, value: 'Former smoker', direction: 'increases' },
{ feature: 'Medication Adherence', contribution: -2.1, value: '78% (30-day)', direction: 'decreases' },
{ feature: 'Physical Activity', contribution: -1.8, value: '6.4k steps/day', direction: 'decreases' },
{ feature: 'BMI', contribution: 1.5, value: '28.4', direction: 'increases' }];


export const riskDistribution = [
{ name: 'Low', value: 812, tone: '#0e9f7e' },
{ name: 'Medium', value: 412, tone: '#d98a00' },
{ name: 'High', value: 23, tone: '#d13f3f' }];


export const federatedNodes = [
{ id: 'HOSP-A', name: 'Hospital A — Northside General', status: 'Connected', patients: 512, lastSync: '3 min ago', contribution: 41 },
{ id: 'HOSP-B', name: 'Hospital B — Lakeview Medical', status: 'Connected', patients: 438, lastSync: '4 min ago', contribution: 35 },
{ id: 'HOSP-C', name: 'Hospital C — Riverbend Clinic', status: 'Connected', patients: 297, lastSync: '6 min ago', contribution: 24 }];


export const federatedRounds = [
{ round: 43, accuracy: 89.1, duration: '14 m', status: 'Completed', model: 'CVD-Risk-v3.0' },
{ round: 44, accuracy: 89.8, duration: '13 m', status: 'Completed', model: 'CVD-Risk-v3.1' },
{ round: 45, accuracy: 90.3, duration: '15 m', status: 'Completed', model: 'CVD-Risk-v3.1' },
{ round: 46, accuracy: 90.9, duration: '12 m', status: 'Completed', model: 'CVD-Risk-v3.2' },
{ round: 47, accuracy: 91.4, duration: '13 m', status: 'Completed', model: 'CVD-Risk-v3.2' }];


export const accuracyTrend = federatedRounds.map((r) => ({ t: `R${r.round}`, accuracy: r.accuracy }));