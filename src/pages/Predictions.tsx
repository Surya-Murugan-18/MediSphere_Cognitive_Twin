import { useQuery } from '@tanstack/react-query';
import { BrainIcon, GaugeIcon, HeartPulseIcon, LayersIcon } from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader';
import { KpiCard } from '../components/ui/KpiCard';
import { Card, CardBody, CardHeader, DefinitionRow } from '../components/ui/Card';
import { Badge, RiskBadge } from '../components/ui/Badge';
import { LinkButton } from '../components/ui/Button';
import { TableShell, Td, Th, Tr } from '../components/ui/Table';
import { AiNotice } from '../components/ui/AiNotice';
import { SkeletonBlock, SkeletonRows, EmptyState } from '../components/ui/States';
import { RiskDonut } from '../components/charts/RiskDonut';
import {
  getPredictions,
  getPredictionStats,
  getRiskDistribution,
} from '../api/predictions';

/**
 * Predictions page — Phase 4 API-wired.
 *
 * All data comes from the backend API.
 * No static imports from src/data/predictions.ts remain.
 * UI layout, colors, components, and chart design are unchanged from the original.
 */
export function Predictions() {
  // ── KPI stats ─────────────────────────────────────────────────────────────
  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['prediction-stats'],
    queryFn:  getPredictionStats,
    staleTime: 30_000,
  });

  // ── Risk distribution (donut chart) — per tasks.md F4.2 ─────────────────
  const { data: rawDistribution = [], isLoading: distLoading } = useQuery({
    queryKey: ['prediction-distribution'],
    queryFn:  getRiskDistribution,
    staleTime: 30_000,
  });

  // Map API data to RiskDonut format — add tone colors for High/Medium/Low
  const RISK_TONES: Record<string, string> = {
    High:   '#d13f3f',
    Medium: '#d98a00',
    Low:    '#0e9f7e',
  };
  const donutData = rawDistribution.map((item) => ({
    name:  item.name,
    value: item.value,
    tone:  RISK_TONES[item.name] ?? '#94a3b8',
  }));
  const donutTotal = donutData.reduce((sum, d) => sum + d.value, 0);

  // ── All predictions (table + primary card) ────────────────────────────────
  const { data: predictionsPage, isLoading: predsLoading } = useQuery({
    queryKey: ['predictions'],
    queryFn:  () => getPredictions({ size: 50 }),
    staleTime: 30_000,
  });

  const allPredictions = predictionsPage?.content ?? [];

  // Primary card: first High-risk prediction from the list (not hardcoded to any patient)
  const primary = allPredictions.find((p) => p.category === 'High') ?? allPredictions[0];
  const diabetes = allPredictions.find(
    (p) => p.model === 'DM-Complication-v2.4' && p.patientId === primary?.patientId,
  );
  const readmission = allPredictions.find(
    (p) => p.model === 'Readmit-30d-v1.8' && p.patientId === primary?.patientId,
  );

  // Derive stats with safe fallbacks while loading
  const total         = stats?.total         ?? 0;
  const avgAccuracy   = stats?.avgAccuracy   ?? 0;
  const highRiskCount = stats?.highRiskCount ?? 0;
  const latestRound   = stats?.latestRound   ?? 47;

  return (
    <div>
      <PageHeader
        title="AI Risk Prediction Engine"
        subtitle="Privacy-preserving federated models scoring the cohort from each patient's Digital Health Twin"
        meta={
          <>
            <Badge tone="info">Federated round {latestRound}</Badge>
            <Badge tone="healthy" dot>TensorFlow Federated active</Badge>
          </>
        }
        actions={<LinkButton to="/federated">Federated learning</LinkButton>}
      />

      <section aria-label="Prediction indicators" className="grid grid-cols-2 gap-3 xl:grid-cols-4">
        <KpiCard
          label="Risk Predictions"
          value={statsLoading ? '…' : String(total)}
          caption="Total predictions on record"
          tone="info"
          icon={<BrainIcon className="h-4 w-4" />}
        />
        <KpiCard
          label="Model Accuracy"
          value={statsLoading ? '…' : `${avgAccuracy}%`}
          caption="Average confidence score"
          tone="healthy"
          icon={<GaugeIcon className="h-4 w-4" />}
        />
        <KpiCard
          label="High Risk Patients"
          value={statsLoading ? '…' : String(highRiskCount)}
          caption="Require clinician review"
          tone="critical"
          emphasis
          icon={<HeartPulseIcon className="h-4 w-4" />}
        />
        <KpiCard
          label="Federated Round"
          value={statsLoading ? '…' : String(latestRound)}
          caption="Latest completed round"
          tone="neutral"
          icon={<LayersIcon className="h-4 w-4" />}
          to="/federated"
        />
      </section>

      <div className="mt-5 grid grid-cols-1 gap-4 xl:grid-cols-3">
        {/* ── Primary CVD prediction card ── */}
        <Card className="xl:col-span-2">
          <CardHeader
            title="Primary prediction"
            description={primary
              ? `${primary.patientId} · ${primary.model}`
              : 'No high-risk predictions yet'}
          />
          <CardBody className="space-y-4">
            <AiNotice kind="prediction" />
            {predsLoading ? (
              <SkeletonBlock className="h-40" />
            ) : !primary ? (
              <EmptyState
                title="No predictions yet"
                description="Predictions will appear once the AI models have run for at least one patient."
              />
            ) : (
              <div className="grid grid-cols-1 gap-4 lg:grid-cols-5">
                <div className="lg:col-span-2">
                  <p className="text-xs uppercase tracking-wide text-slate-500">
                    {primary.label}
                  </p>
                  <p className={`mt-1 text-5xl font-semibold tabular ${
                    primary.category === 'High' ? 'text-critical-600'
                    : primary.category === 'Medium' ? 'text-amber-600'
                    : 'text-teal-600'
                  }`}>
                    {primary.value}%
                  </p>
                  <p className="mt-2">
                    <RiskBadge risk={primary.category} />
                  </p>
                  <div className="mt-4">
                    <RiskScale value={primary.value} />
                  </div>
                </div>
                <div className="lg:col-span-3">
                  <dl className="rounded-md border border-slate-200 px-3">
                    <DefinitionRow label="Model"            value={primary.model} />
                    <DefinitionRow label="Federated round"  value={String(primary.federatedRound)} />
                    <DefinitionRow label="Prediction"       value={`${primary.value}%`} />
                    <DefinitionRow label="Confidence"       value={`${primary.confidence}%`} />
                    <DefinitionRow label="Calibration"      value={<Badge tone="healthy">{primary.calibration}</Badge>} />
                  </dl>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {/* predictionId from API — not hardcoded */}
                    <LinkButton to={`/explain?predictionId=${primary.id}`} variant="primary" size="sm">
                      View explanation
                    </LinkButton>
                    {/* patientId from API — not hardcoded */}
                    <LinkButton to={`/twins/${primary.patientId}`} size="sm">
                      View digital twin
                    </LinkButton>
                    <LinkButton to={`/care-plans/new?patientId=${primary.patientId}`} size="sm">
                      Create care plan
                    </LinkButton>
                  </div>
                </div>
              </div>
            )}
          </CardBody>
        </Card>

        {/* ── Secondary prediction cards + risk donut ── */}
        <div className="space-y-4">
          <Card>
            <CardHeader title="Risk distribution" description="Current cohort by category" />
            <CardBody>
              {distLoading ? (
                <SkeletonBlock className="h-[140px]" />
              ) : donutData.length === 0 ? (
                <p className="text-sm text-slate-500">No data available.</p>
              ) : (
                <RiskDonut
                  data={donutData}
                  centerLabel="predictions"
                  centerValue={donutTotal.toLocaleString()}
                  height={140}
                />
              )}
            </CardBody>
          </Card>
          <Card>
            <CardHeader
              title="Diabetes complication risk"
              description={diabetes?.model ?? 'DM-Complication-v2.4'}
            />
            <CardBody>
              {predsLoading ? (
                <SkeletonBlock className="h-16" />
              ) : diabetes ? (
                <>
                  <p className={`text-3xl font-semibold tabular ${
                    diabetes.category === 'High' ? 'text-critical-600' : 'text-amber-600'
                  }`}>
                    {diabetes.value}%
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    12-month horizon · confidence {diabetes.confidence}%
                  </p>
                  <div className="mt-3">
                    <RiskScale value={diabetes.value} />
                  </div>
                </>
              ) : (
                <p className="text-sm text-slate-500">No prediction available</p>
              )}
            </CardBody>
          </Card>
          <Card>
            <CardHeader
              title="Readmission risk"
              description={readmission?.model ?? 'Readmit-30d-v1.8'}
            />
            <CardBody>
              {predsLoading ? (
                <SkeletonBlock className="h-16" />
              ) : readmission ? (
                <>
                  <p className="text-3xl font-semibold tabular text-amber-600">
                    {readmission.value}%
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    30-day horizon · confidence {readmission.confidence}%
                  </p>
                  <p className="mt-2">
                    <Badge tone={readmission.calibration === 'Calibrated' ? 'healthy' : 'warning'}>
                      {readmission.calibration}
                    </Badge>
                  </p>
                </>
              ) : (
                <p className="text-sm text-slate-500">No prediction available</p>
              )}
            </CardBody>
          </Card>
        </div>
      </div>

      {/* ── All predictions table ── */}
      <Card className="mt-4">
        <CardHeader
          title="All predictions"
          description="Every model output awaiting or following clinical review"
        />
        {predsLoading ? (
          <SkeletonRows rows={5} cols={8} />
        ) : allPredictions.length === 0 ? (
          <div className="p-4">
            <EmptyState
              title="No predictions yet"
              description="Predictions will appear once the AI models have run."
            />
          </div>
        ) : (
          <TableShell>
            <thead>
              <tr>
                <Th>Patient</Th>
                <Th>Prediction</Th>
                <Th>Model</Th>
                <Th align="right">Value</Th>
                <Th>Category</Th>
                <Th align="right">Confidence</Th>
                <Th>Updated</Th>
                <Th align="right">Action</Th>
              </tr>
            </thead>
            <tbody>
              {allPredictions.map((prediction) => (
                <Tr key={prediction.id}>
                  <Td className="font-medium text-slate-900">{prediction.patientId}</Td>
                  <Td className="text-slate-600">{prediction.label}</Td>
                  <Td className="font-mono-clinical text-xs text-slate-500">{prediction.model}</Td>
                  <Td align="right" className="tabular font-medium">
                    {prediction.value}%
                  </Td>
                  <Td>
                    <RiskBadge risk={prediction.category} />
                  </Td>
                  <Td align="right" className="tabular text-slate-500">
                    {prediction.confidence}%
                  </Td>
                  <Td className="text-slate-500">{formatRelative(prediction.createdAt)}</Td>
                  <Td align="right">
                    {/* predictionId from API — not hardcoded */}
                    <LinkButton
                      to={`/explain?predictionId=${prediction.id}`}
                      size="sm"
                      variant="ghost"
                    >
                      Explain
                    </LinkButton>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </TableShell>
        )}
      </Card>
    </div>
  );
}

// ── Risk scale sub-component — unchanged from original ────────────────────

function RiskScale({ value }: { value: number }) {
  return (
    <div>
      <div
        className="relative h-2 w-full overflow-hidden rounded-full bg-slate-100"
        role="img"
        aria-label={`Risk ${value} percent`}
      >
        <div className="absolute inset-y-0 left-0 w-[10%] bg-teal-500/70" />
        <div className="absolute inset-y-0 left-[10%] w-[10%] bg-amber-500/70" />
        <div className="absolute inset-y-0 left-[20%] right-0 bg-critical-500/70" />
        <span
          className="absolute -top-0.5 h-3 w-1 rounded-sm bg-slate-900"
          style={{ left: `calc(${Math.min(value, 100)}% - 2px)` }}
          aria-hidden="true"
        />
      </div>
      <div className="mt-1 flex justify-between text-2xs text-slate-400">
        <span>Low &lt;10%</span>
        <span>Borderline 10–20%</span>
        <span>High &gt;20%</span>
      </div>
    </div>
  );
}

function formatRelative(ts?: string | null): string {
  if (!ts) return '—';
  try {
    const diff = Date.now() - new Date(ts).getTime();
    const mins = Math.floor(diff / 60_000);
    if (mins < 1)  return 'Just now';
    if (mins < 60) return `${mins} min ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24)  return `${hrs} h ago`;
    return `${Math.floor(hrs / 24)}d ago`;
  } catch { return '—'; }
}
