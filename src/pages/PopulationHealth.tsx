import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '../components/ui/PageHeader';
import { KpiCard } from '../components/ui/KpiCard';
import { Card, CardBody, CardHeader } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { TableShell, Td, Th, Tr } from '../components/ui/Table';
import { ProgressBar, adherenceTone } from '../components/ui/Progress';
import { RiskDonut } from '../components/charts/RiskDonut';
import { CategoryBarChart } from '../components/charts/CategoryBarChart';
import { TrendChart } from '../components/charts/TrendChart';
import { SkeletonBlock } from '../components/ui/States';
import {
  getPopulationStats,
  getRiskDistribution,
  getConditionDistribution,
  getHospitalOverview,
  getAlertTrend,
  getPopulationCategories,
  getPopulationAdherenceTrend,
  getPopulationRiskTrend,
} from '../api/population';

export function PopulationHealth() {
  const { data: stats } = useQuery({
    queryKey: ['population-stats'],
    queryFn: getPopulationStats,
    staleTime: 60_000,
  });

  const { data: riskDist } = useQuery({
    queryKey: ['population-risk-distribution'],
    queryFn: getRiskDistribution,
    staleTime: 60_000,
  });

  const { data: conditionDist } = useQuery({
    queryKey: ['population-condition-distribution'],
    queryFn: getConditionDistribution,
    staleTime: 60_000,
  });

  const { data: hospitals } = useQuery({
    queryKey: ['population-hospitals'],
    queryFn: getHospitalOverview,
    staleTime: 60_000,
  });

  const { data: alertTrendData } = useQuery({
    queryKey: ['population-alert-trend'],
    queryFn: getAlertTrend,
    staleTime: 60_000,
  });

  const { data: categories } = useQuery({
    queryKey: ['population-categories'],
    queryFn: getPopulationCategories,
    staleTime: 60_000,
  });

  // Population-level adherence trend across active care plans.
  const { data: adherenceTrendData } = useQuery({
    queryKey: ['population-adherence-trend'],
    queryFn: getPopulationAdherenceTrend,
    staleTime: 60_000,
  });

  // Historical population risk snapshots derived from persisted predictions.
  const { data: riskTrendData } = useQuery({
    queryKey: ['population-risk-trend'],
    queryFn: getPopulationRiskTrend,
    staleTime: 60_000,
  });

  const totalPatients   = stats?.totalPatients   ?? 0;
  const highRisk        = stats?.highRiskCount    ?? 0;
  const activeAlerts    = stats?.activeAlerts     ?? 0;
  const activePlans     = stats?.activePlans      ?? 0;
  const avgAdherence    = stats?.avgAdherence     ?? 0;

  return (
    <div>
      <PageHeader
        title="Population health"
        subtitle="Cohort-level risk, condition prevalence and care-plan performance across the federation"
        meta={<Badge tone="info">{hospitals?.length ?? 3} participating hospitals</Badge>}
      />

      <section aria-label="Population indicators" className="grid grid-cols-2 gap-3 xl:grid-cols-4">
        <KpiCard label="Total Patients"   value={totalPatients.toLocaleString()} caption="Across hospitals" tone="info" />
        <KpiCard label="High Risk"         value={String(highRisk)}  caption={`${((highRisk / (totalPatients || 1)) * 100).toFixed(1)}% of the cohort`} tone="critical" emphasis />
        <KpiCard label="Active Alerts"     value={String(activeAlerts)} caption="Today" tone="warning" to="/alerts" />
        <KpiCard label="Active Care Plans" value={String(activePlans)} caption={`${avgAdherence.toFixed(0)}% average adherence`} tone="healthy" to="/care-plans" />
      </section>

      <div className="mt-5 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Card>
          <CardHeader title="Risk distribution" description="Current cohort stratification" />
          <CardBody>
            {riskDist ? (
              <RiskDonut data={riskDist.map(d => ({ ...d, tone: d.tone ?? '#94a3b8' }))} centerLabel="patients" centerValue={totalPatients.toLocaleString()} height={180} />
            ) : (
              <SkeletonBlock className="h-[180px]" />
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Condition distribution" description="Primary documented conditions" />
          <CardBody>
            {conditionDist ? (
              <CategoryBarChart data={conditionDist} layout="vertical" height={200} />
            ) : (
              <SkeletonBlock className="h-[200px]" />
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Alert trends" description="Alert volume over the last 7 days" />
          <CardBody>
            {alertTrendData ? (
              <TrendChart
                data={alertTrendData}
                xKey="t"
                series={[
                  { key: 'alerts',   name: 'All alerts', color: '#1f6fd0' },
                  { key: 'critical', name: 'Critical',   color: '#d13f3f' },
                ]}
                height={200}
              />
            ) : (
              <SkeletonBlock className="h-[200px]" />
            )}
          </CardBody>
        </Card>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader
            title="Population risk trends"
            description={
              riskTrendData && riskTrendData.length > 0
                ? 'High and medium risk cohorts from available prediction history'
                : 'Historical risk data is not available'
            }
          />
          <CardBody>
            {riskTrendData ? (
              riskTrendData.length > 0 ? (
                <TrendChart
                  data={riskTrendData}
                  xKey="t"
                  series={[
                    { key: 'medium', name: 'Medium risk', color: '#d98a00' },
                    { key: 'high',   name: 'High risk',   color: '#d13f3f' },
                  ]}
                  height={220}
                />
              ) : (
                <div className="flex h-[220px] items-center justify-center text-sm text-slate-500">
                  No historical risk data available.
                </div>
              )
            ) : (
              <SkeletonBlock className="h-[220px]" />
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Care plan adherence" description="Weekly adherence across all active plans" />
          <CardBody>
            {adherenceTrendData ? (
              adherenceTrendData.length > 0 ? (
                <TrendChart
                  data={adherenceTrendData.map(p => ({ t: p.t, value: p.value }))}
                  xKey="t"
                  series={[{ key: 'value', name: 'Adherence (%)', color: '#0e9f7e' }]}
                  domain={[0, 100]}
                  area
                  height={220}
                />
              ) : (
                <div className="flex h-[220px] items-center justify-center text-sm text-slate-500">
                  No adherence data available for active care plans.
                </div>
              )
            ) : (
              <SkeletonBlock className="h-[220px]" />
            )}
          </CardBody>
        </Card>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Card>
          <CardHeader title="Population categories" />
          <CardBody className="space-y-3 pt-0">
            {(categories ?? []).map((category) => (
              <div key={category.name} className="border-b border-slate-100 py-2.5 last:border-0">
                <div className="flex items-center justify-between gap-3">
                  <p className="text-sm font-medium text-slate-900">{category.name}</p>
                  <Badge tone={category.tone as any}>{category.trend}</Badge>
                </div>
                <p className="mt-0.5 text-xs text-slate-500">
                  {category.cohort.toLocaleString()} patients · {category.highRisk} high risk
                </p>
              </div>
            ))}
          </CardBody>
        </Card>

        <Card className="xl:col-span-2">
          <CardHeader
            title="Hospital overview"
            description="Federation members contributing to the global model"
          />
          <TableShell>
            <thead>
              <tr>
                <Th>Hospital</Th>
                <Th align="right">Patients</Th>
                <Th align="right">High risk</Th>
                <Th className="w-40">Adherence</Th>
                <Th align="right">Alerts today</Th>
              </tr>
            </thead>
            <tbody>
              {(hospitals ?? []).map((hospital) => (
                <Tr key={hospital.name}>
                  <Td className="font-medium text-slate-900">{hospital.name}</Td>
                  <Td align="right" className="tabular">{hospital.patients}</Td>
                  <Td align="right" className="tabular text-critical-600">{hospital.highRisk}</Td>
                  <Td>
                    <ProgressBar
                      value={hospital.adherence}
                      tone={adherenceTone(hospital.adherence)}
                      caption={`${hospital.adherence}%`}
                      label=""
                    />
                  </Td>
                  <Td align="right" className="tabular">{hospital.alerts}</Td>
                </Tr>
              ))}
            </tbody>
          </TableShell>
        </Card>
      </div>
    </div>
  );
}
