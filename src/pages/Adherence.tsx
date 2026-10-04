import React from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '../components/ui/PageHeader';
import { Card, CardBody, CardHeader, DefinitionRow } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { LinkButton } from '../components/ui/Button';
import { ProgressBar, adherenceTone } from '../components/ui/Progress';
import { Timeline } from '../components/ui/Timeline';
import { TrendChart } from '../components/charts/TrendChart';
import { EmptyState, SkeletonBlock } from '../components/ui/States';
import { getCarePlan, getCarePlanTimeline } from '../api/carePlans';
import { getAdherence, getAdherenceTrend, getOutcomes } from '../api/adherence';

/**
 * Adherence — Phase 6 wiring.
 *
 * All static imports from src/data/carePlans removed.
 * Real data from:
 *   - getCarePlan(planId)            — plan header info
 *   - getAdherence(planId)           — overall + breakdown
 *   - getAdherenceTrend(planId, 6)   — 6-week trend chart
 *   - getOutcomes(planId)            — clinical outcome metrics
 *   - getCarePlanTimeline(planId)    — timeline events
 *
 * UI layout, charts, and component structure unchanged.
 */
export function Adherence() {
  const { planId } = useParams<{ planId: string }>();

  // ── Queries ───────────────────────────────────────────────────────────

  const {
    data: plan,
    isLoading: planLoading,
    isError: planError,
  } = useQuery({
    queryKey: ['care-plan', planId],
    queryFn:  () => getCarePlan(planId!),
    enabled:  Boolean(planId),
    retry:    (count, err: any) => err?.response?.status !== 404 && count < 2,
  });

  const { data: adherence, isLoading: adhLoading } = useQuery({
    queryKey: ['adherence', planId],
    queryFn:  () => getAdherence(planId!),
    enabled:  Boolean(planId),
  });

  const { data: trendData = [], isLoading: trendLoading } = useQuery({
    queryKey: ['adherence-trend', planId],
    queryFn:  () => getAdherenceTrend(planId!, 6),
    enabled:  Boolean(planId),
  });

  const { data: outcomes = [] } = useQuery({
    queryKey: ['outcomes', planId],
    queryFn:  () => getOutcomes(planId!),
    enabled:  Boolean(planId),
  });

  const { data: timeline = [] } = useQuery({
    queryKey: ['care-plan-timeline', planId],
    queryFn:  () => getCarePlanTimeline(planId!),
    enabled:  Boolean(planId),
  });

  // ── Guard — plan not found ────────────────────────────────────────────
  if (!planLoading && (planError || !plan)) {
    return <Navigate to="/care-plans" replace />;
  }

  const overall   = adherence?.overall ?? 0;
  const breakdown = adherence?.breakdown ?? [];

  // Primary outcome (first item)
  const primaryOutcome = outcomes[0] ?? null;

  return (
    <div>
      <PageHeader
        title="Patient adherence"
        subtitle={plan
          ? `${plan.patientName} · Plan ${plan.id} · ${plan.goal}`
          : 'Loading…'}
        backTo={{ to: '/care-plans', label: 'Back to care plans' }}
        meta={
          <>
            <Badge tone={adherenceTone(overall)}>Overall adherence {overall}%</Badge>
            {primaryOutcome && (
              <Badge tone="healthy">Outcome trend {primaryOutcome.trend}</Badge>
            )}
          </>
        }
        actions={
          plan ? (
            <LinkButton to={`/twins/${plan.patientId}`}>View digital twin</LinkButton>
          ) : undefined
        }
      />

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        {/* Trend chart */}
        <Card className="xl:col-span-2">
          <CardHeader
            title="Adherence trend"
            description="Weekly adherence since the plan was approved"
          />
          <CardBody>
            {trendLoading ? (
              <SkeletonBlock className="h-56" />
            ) : trendData.length === 0 ? (
              <EmptyState title="No trend data" description="Adherence data will appear here as weekly records are recorded." />
            ) : (
              <TrendChart
                data={trendData}
                xKey="t"
                series={[{ key: 'value', name: 'Adherence (%)', color: '#0e9f7e' }]}
                domain={[40, 100]}
                area
                height={220}
              />
            )}
          </CardBody>
        </Card>

        {/* Overall + breakdown */}
        <Card>
          <CardHeader title="Overall adherence" />
          <CardBody className="space-y-4">
            {adhLoading ? (
              <SkeletonBlock className="h-24" />
            ) : (
              <>
                <div>
                  <p className="text-4xl font-semibold tabular text-slate-900">{overall}%</p>
                  <p className="mt-1 text-xs text-slate-500">
                    30-day rolling average across all plan components
                  </p>
                </div>
                <div className="space-y-3">
                  {breakdown.map((item) => (
                    <ProgressBar
                      key={item.label}
                      label={item.label}
                      value={item.value}
                      tone={adherenceTone(item.value)}
                    />
                  ))}
                </div>
              </>
            )}
          </CardBody>
        </Card>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
        {/* Primary outcome */}
        <Card>
          <CardHeader title="Outcome" description="Primary clinical measure for this plan" />
          <CardBody className="pt-0">
            {primaryOutcome ? (
              <dl>
                <DefinitionRow
                  label={`Previous ${primaryOutcome.metric}`}
                  value={`${primaryOutcome.baseline}${primaryOutcome.unit}`}
                />
                <DefinitionRow
                  label={`Current ${primaryOutcome.metric}`}
                  value={<span className="text-teal-700">{primaryOutcome.current}{primaryOutcome.unit}</span>}
                />
                <DefinitionRow
                  label="Goal"
                  value={`< ${primaryOutcome.goal}${primaryOutcome.unit}`}
                />
                <DefinitionRow
                  label="Trend"
                  value={
                    <Badge tone={primaryOutcome.trend === 'improving' ? 'healthy' : primaryOutcome.trend === 'worsening' ? 'critical' : 'neutral'}>
                      {primaryOutcome.trend.charAt(0).toUpperCase() + primaryOutcome.trend.slice(1)}
                    </Badge>
                  }
                />
              </dl>
            ) : (
              <p className="text-sm text-slate-500">No outcome data recorded yet.</p>
            )}
          </CardBody>
        </Card>

        {/* Care plan timeline */}
        <Card className="xl:col-span-2">
          <CardHeader
            title="Care plan timeline"
            description="Approval, intervention and outcome events"
          />
          <CardBody>
            {timeline.length > 0 ? (
              <Timeline
                items={timeline.map((e) => ({
                  id:        e.id,
                  timestamp: e.timestamp ?? '',
                  title:     e.title,
                  detail:    e.detail ?? '',
                  tone:      e.tone as any,
                }))}
              />
            ) : (
              <p className="text-sm text-slate-500">No timeline events yet.</p>
            )}
          </CardBody>
        </Card>
      </div>
    </div>
  );
}
