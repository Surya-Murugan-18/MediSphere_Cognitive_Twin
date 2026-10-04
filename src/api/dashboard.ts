import api from './client';
import {
  DashboardStatsSchema,
  type DashboardStats,
} from '../schemas/dashboard.schema';
import { getRiskDistribution } from './population';
import type { RiskDistributionItem } from '../schemas/population.schema';

/**
 * Dashboard API service.
 * Per design.md §10 and tasks.md F7.8.
 *
 * GET /api/dashboard/stats       — all 8 KPI values in one call
 * getRiskDistribution()          — delegated to population.ts (same backend endpoint)
 */

export async function getDashboardStats(): Promise<DashboardStats> {
  const response = await api.get('/api/dashboard/stats');
  return DashboardStatsSchema.parse(response.data);
}

// Re-export so Dashboard.tsx can import from a single module
export { getRiskDistribution };
export type { RiskDistributionItem };
