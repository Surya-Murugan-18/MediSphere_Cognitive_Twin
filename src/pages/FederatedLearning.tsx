import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowDownIcon, ShieldCheckIcon } from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader';
import { Card, CardBody, CardHeader, DefinitionRow } from '../components/ui/Card';
import { Badge, StatusDot } from '../components/ui/Badge';
import { ProgressBar } from '../components/ui/Progress';
import { TableShell, Td, Th, Tr } from '../components/ui/Table';
import { TrendChart } from '../components/charts/TrendChart';
import { Notice, SkeletonBlock, SkeletonRows } from '../components/ui/States';
import {
  getFederatedNodes,
  getCurrentFederatedRound,
  getFederatedRounds,
} from '../api/predictions';
import type { FederatedRound } from '../schemas/prediction.schema';

/**
 * FederatedLearning page — Phase 4 API-wired.
 *
 * Hospital nodes, current round, round history, and accuracy trend
 * all come from the backend API via StubFederatedOrchestrator.
 * No static data imports from src/data/predictions.ts remain.
 * UI layout, charts, cards, and colours are unchanged.
 * No WebSocket or Kafka in Phase 4.
 */

const pipeline = ['Local Training', 'Model Updates', 'Federated Aggregation', 'Global Model'];

export function FederatedLearning() {
  // ── API queries ───────────────────────────────────────────────────────────

  const { data: nodes = [], isLoading: nodesLoading } = useQuery({
    queryKey: ['federated-nodes'],
    queryFn:  getFederatedNodes,
    staleTime: 60_000,
  });

  const { data: currentRound, isLoading: roundLoading } = useQuery({
    queryKey: ['federated-round-current'],
    queryFn:  getCurrentFederatedRound,
    staleTime: 60_000,
  });

  const { data: roundsPage, isLoading: historyLoading } = useQuery({
    queryKey: ['federated-rounds'],
    queryFn:  () => getFederatedRounds(0, 10),
    staleTime: 60_000,
  });

  const rounds: FederatedRound[] = roundsPage?.content ?? [];

  // Accuracy trend derived from round history (sorted oldest→newest for chart)
  const accuracyTrend = [...rounds]
    .sort((a, b) => a.round - b.round)
    .map((r) => ({ t: `R${r.round}`, accuracy: r.accuracy }));

  return (
    <div>
      <PageHeader
        title="Federated learning"
        subtitle="TensorFlow Federated coordination across participating hospitals — patient data never leaves its institution"
        meta={
          <>
            <Badge tone="info">
              Model {currentRound?.model ?? 'CVD-Risk-v3.2'}
            </Badge>
            <Badge tone="healthy" dot>
              Round {currentRound?.round ?? '…'}{' '}
              {currentRound?.status === 'Completed' ? 'completed' : currentRound?.status ?? ''}
            </Badge>
          </>
        }
      />

      <div className="mb-4">
        <Notice tone="healthy" title="Privacy-preserving by design">
          Patient data remains within the hospital. Only model and learning updates are shared
          with the aggregation service.
        </Notice>
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        {/* ── Federation diagram (nodes) ── */}
        <Card className="xl:col-span-2">
          <CardHeader
            title="Federation diagram"
            description="How each round of learning flows through the network"
          />
          <CardBody>
            {nodesLoading ? (
              <SkeletonBlock className="h-40" />
            ) : (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                {nodes.map((node) => (
                  <div key={node.id} className="rounded-lg border border-slate-200 p-3">
                    <div className="flex items-center justify-between gap-2">
                      <p className="truncate text-sm font-medium text-slate-900">
                        {node.hospitalName.split('—')[0].trim()}
                      </p>
                      <StatusDot tone={node.status === 'Connected' ? 'healthy' : 'warning'} />
                    </div>
                    <p className="mt-0.5 truncate text-2xs text-slate-500">
                      {node.hospitalName.split('—')[1]?.trim()}
                    </p>
                    <dl className="mt-2 space-y-1 text-xs">
                      <div className="flex justify-between">
                        <dt className="text-slate-500">Status</dt>
                        <dd className={`font-medium ${node.status === 'Connected' ? 'text-teal-700' : 'text-amber-700'}`}>
                          {node.status}
                        </dd>
                      </div>
                      <div className="flex justify-between">
                        <dt className="text-slate-500">Local cohort</dt>
                        <dd className="tabular text-slate-800">{node.patients}</dd>
                      </div>
                      <div className="flex justify-between">
                        <dt className="text-slate-500">Last sync</dt>
                        <dd className="text-slate-800">{formatRelative(node.lastSync)}</dd>
                      </div>
                    </dl>
                    <div className="mt-2">
                      <ProgressBar
                        value={node.contribution}
                        tone="info"
                        label="Update weight"
                      />
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* Pipeline flow diagram */}
            <div className="mt-4 flex flex-col items-center">
              {pipeline.map((step, index) => (
                <React.Fragment key={step}>
                  <ArrowDownIcon className="my-1.5 h-4 w-4 text-slate-300" aria-hidden="true" />
                  <div
                    className={`w-full max-w-sm rounded-md border px-4 py-2.5 text-center text-sm font-medium ${
                      index === pipeline.length - 1
                        ? 'border-brand-200 bg-brand-50 text-brand-800'
                        : 'border-slate-200 bg-slate-50 text-slate-700'
                    }`}
                  >
                    {step}
                  </div>
                </React.Fragment>
              ))}
            </div>
          </CardBody>
        </Card>

        {/* ── Current round panel ── */}
        <div className="space-y-4">
          <Card>
            <CardHeader title="Current round" />
            <CardBody className="pt-0">
              {roundLoading ? (
                <SkeletonBlock className="h-32" />
              ) : currentRound ? (
                <>
                  <dl>
                    <DefinitionRow label="Model"                value={currentRound.model} />
                    <DefinitionRow label="Federated round"      value={String(currentRound.round)} />
                    <DefinitionRow
                      label="Status"
                      value={
                        <Badge tone={currentRound.status === 'Completed' ? 'healthy' : 'info'} dot>
                          {currentRound.status}
                        </Badge>
                      }
                    />
                    <DefinitionRow
                      label="Participating nodes"
                      value={`${currentRound.nodeContributions?.length ?? 0} of ${nodes.length}`}
                    />
                    <DefinitionRow label="Global accuracy"   value={`${currentRound.accuracy}%`} />
                  </dl>
                  <div className="mt-4 space-y-3">
                    <ProgressBar
                      label="Training progress"
                      value={currentRound.status === 'Completed' ? 100 : 50}
                      tone="healthy"
                      caption={currentRound.status === 'Completed' ? '100%' : 'In progress'}
                    />
                    <ProgressBar
                      label={`Next round (${currentRound.round + 1}) preparation`}
                      value={22}
                      tone="info"
                      caption="22%"
                    />
                  </div>
                </>
              ) : (
                <p className="text-sm text-slate-500">No round data available.</p>
              )}
            </CardBody>
          </Card>

          {/* Privacy controls — static by design (display only) */}
          <Card>
            <CardHeader title="Privacy controls" icon={<ShieldCheckIcon className="h-4 w-4" />} />
            <CardBody className="space-y-2 pt-0 text-sm text-slate-700">
              {[
                'Secure aggregation enabled',
                'Differential privacy noise applied',
                'No raw patient records transmitted',
                'Model updates cryptographically signed',
              ].map((item) => (
                <p
                  key={item}
                  className="flex items-center gap-2 border-b border-slate-100 py-2 last:border-0"
                >
                  <StatusDot tone="healthy" />
                  {item}
                </p>
              ))}
            </CardBody>
          </Card>
        </div>
      </div>

      {/* ── Accuracy trend + round history ── */}
      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader
            title="Accuracy trend"
            description="Global model accuracy across recent rounds"
          />
          <CardBody>
            {historyLoading ? (
              <SkeletonBlock className="h-[220px]" />
            ) : accuracyTrend.length > 0 ? (
              <TrendChart
                data={accuracyTrend}
                xKey="t"
                series={[{ key: 'accuracy', name: 'Accuracy (%)', color: '#1f6fd0' }]}
                domain={[88, 93]}
                area
                height={220}
              />
            ) : (
              <p className="text-sm text-slate-500">No accuracy data available.</p>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader
            title="Round history"
            description="Model versions published by the aggregation service"
          />
          {historyLoading ? (
            <SkeletonRows rows={5} cols={5} />
          ) : (
            <TableShell>
              <thead>
                <tr>
                  <Th align="right">Round</Th>
                  <Th>Model version</Th>
                  <Th align="right">Accuracy</Th>
                  <Th align="right">Duration</Th>
                  <Th>Status</Th>
                </tr>
              </thead>
              <tbody>
                {rounds.map((round) => (
                  <Tr key={round.round}>
                    <Td align="right" className="tabular font-medium text-slate-900">
                      {round.round}
                    </Td>
                    <Td className="font-mono-clinical text-xs text-slate-600">{round.model}</Td>
                    <Td align="right" className="tabular">{round.accuracy}%</Td>
                    <Td align="right" className="tabular text-slate-500">
                      {round.durationMinutes} m
                    </Td>
                    <Td>
                      <Badge tone={round.status === 'Completed' ? 'healthy' : 'info'}>
                        {round.status}
                      </Badge>
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </TableShell>
          )}
        </Card>
      </div>
    </div>
  );
}

function formatRelative(ts?: string | null): string {
  if (!ts) return '—';
  try {
    const diff = Date.now() - new Date(ts).getTime();
    const mins = Math.floor(diff / 60_000);
    if (mins < 1)   return 'Just now';
    if (mins < 60)  return `${mins} min ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24)   return `${hrs} h ago`;
    return `${Math.floor(hrs / 24)}d ago`;
  } catch { return '—'; }
}
