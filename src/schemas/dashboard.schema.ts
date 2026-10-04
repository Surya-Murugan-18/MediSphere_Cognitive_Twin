import { z } from 'zod';

export const DashboardStatsSchema = z.object({
  patientsOnboarded: z.number(),
  fhirResources: z.number(),
  activeAlerts: z.number(),
  wearablesOnline: z.number(),
  highRiskPatients: z.number(),
  activePlans: z.number(),
  avgAdherence: z.number(),
  avgAlertResponseMin: z.number(),
  wearablesOffline: z.number(),
  plansAwaitingApproval: z.number(),
});

export type DashboardStats = z.infer<typeof DashboardStatsSchema>;
