import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ClipboardListIcon, PlusIcon, TrendingDownIcon } from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader';
import { KpiCard } from '../components/ui/KpiCard';
import { Card, CardHeader } from '../components/ui/Card';
import { Badge, RiskBadge } from '../components/ui/Badge';
import { LinkButton } from '../components/ui/Button';
import { FilterSelect } from '../components/ui/Field';
import { TableShell, Td, Th, Tr } from '../components/ui/Table';
import { ProgressBar, adherenceTone } from '../components/ui/Progress';
import { EmptyState, SkeletonRows } from '../components/ui/States';
import { getCarePlans, getCarePlanStats } from '../api/carePlans';
import type { CarePlanFilters } from '../schemas/carePlan.schema';

// ── Status display mapping ────────────────────────────────────────────────
// Maps backend enum values (DRAFT/ACTIVE/REJECTED) to visual tones and labels.

const statusTone: Record<string, 'healthy' | 'warning' | 'neutral' | 'critical' | 'info'> = {
  ACTIVE:   'healthy',
  DRAFT:    'warning',
  REJECTED: 'critical',
};

const statusLabel: Record<string, string> = {
  ACTIVE:   'Active',
  DRAFT:    'Awaiting Approval',
  REJECTED: 'Rejected',
};

// Status filter options map UI label → API enum value (or '' for no filter)
const STATUS_OPTIONS = [
  { label: 'All statuses', value: '' },
  { label: 'Active',               value: 'ACTIVE' },
  { label: 'Awaiting Approval',    value: 'DRAFT' },
  { label: 'Rejected',             value: 'REJECTED' },
] as const;

const RISK_OPTIONS = ['All risk', 'High', 'Medium', 'Low'] as const;

export function CarePlans() {
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = useState('');      // empty = all
  const [riskFilter,   setRiskFilter]   = useState('');      // empty = all
  const [page, setPage] = useState(0);

  // Build filter criteria for the API query
  const filters: CarePlanFilters = {
    status:    statusFilter || undefined,
    risk:      riskFilter   || undefined,
    page,
    size: 20,
  };

  // ── Care plans list query ─────────────────────────────────────────────
  const {
    data: plansPage,
    isLoading: plansLoading,
  } = useQuery({
    queryKey: ['care-plans', filters],
    queryFn:  () => getCarePlans(filters),
  });

  // ── Stats query ───────────────────────────────────────────────────────
  const { data: stats } = useQuery({
    queryKey: ['care-plan-stats'],
    queryFn:  getCarePlanStats,
  });

  const rows = plansPage?.content ?? [];

  // ── KPI values from API (fall back to loading placeholder) ────────────
  const activeCount    = stats ? String(stats.activeCount) : '…';
  const avgAdherence   = stats ? `${Math.round(stats.avgAdherence)}%` : '…';
  const hospitReduct   = stats ? `↓ ${stats.hospitalizationReduction}%` : '…';
  const draftCaption   = stats ? `${stats.draftCount} awaiting provider approval` : '';

  return (
    <div>
      <PageHeader
        title="Precision care management"
        subtitle="AI-assisted care plans with provider approval, intervention tracking and outcome measurement"
        actions={
          <LinkButton to="/care-plans/new" variant="primary">
            <PlusIcon className="h-4 w-4" aria-hidden="true" />
            Create care plan
          </LinkButton>
        }
      />

      <section aria-label="Care plan indicators" className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <KpiCard
          label="Active Care Plans"
          value={activeCount}
          caption={draftCaption}
          tone="info"
          icon={<ClipboardListIcon className="h-4 w-4" />}
        />
        <KpiCard
          label="Adherence"
          value={avgAdherence}
          caption="Rolling 30-day average"
          tone="healthy"
        />
        <KpiCard
          label="Hospitalizations"
          value={hospitReduct}
          caption="Versus pre-intervention baseline"
          tone="healthy"
          icon={<TrendingDownIcon className="h-4 w-4" />}
        />
      </section>

      <Card className="mt-5">
        <CardHeader
          title="Care plans"
          description="Select a plan to review interventions, safety checks and approval status"
        />
        <div className="flex flex-wrap gap-3 border-b border-slate-200 p-4">
          <FilterSelect
            label="Status"
            value={STATUS_OPTIONS.find(o => o.value === statusFilter)?.label ?? 'All statuses'}
            onChange={(label) => {
              const opt = STATUS_OPTIONS.find(o => o.label === label);
              setStatusFilter(opt?.value ?? '');
              setPage(0);
            }}
            options={STATUS_OPTIONS.map(o => o.label)}
          />
          <FilterSelect
            label="Risk"
            value={riskFilter || 'All risk'}
            onChange={(val) => {
              setRiskFilter(val === 'All risk' ? '' : val);
              setPage(0);
            }}
            options={[...RISK_OPTIONS]}
          />
        </div>

        {plansLoading ? (
          <TableShell>
            <thead>
              <tr>
                <Th>Patient</Th><Th>Goal</Th><Th>Risk</Th>
                <Th className="w-40">Adherence</Th><Th>Status</Th>
                <Th>Provider</Th><Th>Last updated</Th><Th align="right">Actions</Th>
              </tr>
            </thead>
            <tbody>
              <SkeletonRows cols={8} rows={5} />
            </tbody>
          </TableShell>
        ) : rows.length === 0 ? (
          <EmptyState
            title="No care plans match these filters"
            description="Adjust the filters above or create a new AI-assisted care plan."
          />
        ) : (
          <TableShell>
            <thead>
              <tr>
                <Th>Patient</Th>
                <Th>Goal</Th>
                <Th>Risk</Th>
                <Th className="w-40">Adherence</Th>
                <Th>Status</Th>
                <Th>Provider</Th>
                <Th>Last updated</Th>
                <Th align="right">Actions</Th>
              </tr>
            </thead>
            <tbody>
              {rows.map((plan) => (
                <Tr key={plan.id}>
                  <Td className="whitespace-nowrap font-medium text-slate-900">{plan.patientName}</Td>
                  <Td className="text-slate-700">{plan.goal}</Td>
                  <Td>
                    <RiskBadge risk={plan.riskLevel} />
                  </Td>
                  <Td>
                    <ProgressBar
                      value={plan.adherence}
                      tone={adherenceTone(plan.adherence)}
                      caption={`${plan.adherence}%`}
                      label=""
                    />
                  </Td>
                  <Td>
                    <Badge tone={statusTone[plan.status] ?? 'neutral'}>
                      {statusLabel[plan.status] ?? plan.status}
                    </Badge>
                  </Td>
                  <Td className="whitespace-nowrap text-slate-600">
                    {plan.providerId ?? '—'}
                  </Td>
                  <Td className="whitespace-nowrap text-slate-500">
                    {plan.updatedAt ? new Date(plan.updatedAt).toLocaleDateString() : '—'}
                  </Td>
                  <Td align="right">
                    <div className="flex justify-end gap-1.5 whitespace-nowrap text-xs font-medium">
                      <button
                        type="button"
                        onClick={() => navigate(`/care-plans/${plan.id}/review`)}
                        className="rounded px-1.5 py-1 text-brand-600 hover:bg-brand-50"
                      >
                        View
                      </button>
                      <button
                        type="button"
                        onClick={() => navigate(`/care-plans/${plan.id}/edit`)}
                        className="rounded px-1.5 py-1 text-brand-600 hover:bg-brand-50"
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        onClick={() => navigate(`/care-plans/${plan.id}/review`)}
                        className="rounded px-1.5 py-1 text-brand-600 hover:bg-brand-50"
                      >
                        Review
                      </button>
                      <button
                        type="button"
                        onClick={() => navigate(`/care-plans/${plan.id}/adherence`)}
                        className="rounded px-1.5 py-1 text-brand-600 hover:bg-brand-50"
                      >
                        Track
                      </button>
                    </div>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </TableShell>
        )}

        {/* Pagination */}
        {plansPage && plansPage.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-xs text-slate-600">
            <span>
              Page {plansPage.page + 1} of {plansPage.totalPages} · {plansPage.totalElements} plans
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                disabled={plansPage.first}
                onClick={() => setPage(p => Math.max(0, p - 1))}
                className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
              >
                Previous
              </button>
              <button
                type="button"
                disabled={plansPage.last}
                onClick={() => setPage(p => p + 1)}
                className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </Card>
    </div>
  );
}
