import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ClockIcon, LinkIcon } from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader';
import { Card, CardBody, CardHeader, DefinitionRow } from '../components/ui/Card';
import { Badge, RiskBadge, StatusDot } from '../components/ui/Badge';
import { LinkButton } from '../components/ui/Button';
import { Tabs } from '../components/ui/Tabs';
import { TableShell, Td, Th, Tr } from '../components/ui/Table';
import { Timeline } from '../components/ui/Timeline';
import { EmptyState, Notice, SkeletonBlock, SkeletonRows } from '../components/ui/States';
import { ProgressBar, adherenceTone } from '../components/ui/Progress';
import { AiNotice } from '../components/ui/AiNotice';
import { getPatient, getPatientTimeline } from '../api/patients';
import { getRecentLabResults, getLabResults } from '../api/labs';
import { getPatientPredictions } from '../api/predictions';
import { getPatientAlerts } from '../api/alerts';
import { getActiveCarePlan } from '../api/carePlans';
import { getCurrentVitals, type VitalsSnapshot } from '../api/vitals';
import { useVitalsStream } from '../hooks/useVitalsStream';
import { useAlertStream } from '../hooks/useAlertStream';
import type { TimelineEvent } from '../types/clinical';
import type { LabResult } from '../schemas/lab.schema';
import type { Prediction } from '../schemas/prediction.schema';

const tabs = [
  { id: 'overview', label: 'Overview' },
  { id: 'twin',     label: 'Health Twin' },
  { id: 'vitals',   label: 'Vitals' },
  { id: 'labs',     label: 'Labs' },
  { id: 'risks',    label: 'Risks' },
  { id: 'alerts',   label: 'Alerts' },
  { id: 'care',     label: 'Care Plan' },
];

export function Patient360() {
  const { patientId } = useParams<{ patientId: string }>();
  const [tab, setTab] = useState('overview');

  const { isConnected: vitalsStreamConnected, lastUpdated: vitalsLiveUpdated, liveSnapshot } =
    useVitalsStream(patientId);
  useAlertStream();

  // ── Patient query ─────────────────────────────────────────────────────────
  const {
    data: patient,
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ['patient', patientId],
    queryFn:  () => getPatient(patientId!),
    enabled:  Boolean(patientId),
    retry:    (count, err: any) => err?.response?.status !== 404 && count < 2,
  });

  // ── Timeline query (overview tab) ─────────────────────────────────────────
  const { data: timeline = [] } = useQuery({
    queryKey: ['patient-timeline', patientId],
    queryFn:  () => getPatientTimeline(patientId!),
    enabled:  Boolean(patientId) && tab === 'overview',
  });

  // ── Phase 3: top-3 recent labs (overview summary + labs tab) ─────────────
  const { data: recentLabs = [] } = useQuery({
    queryKey: ['patient-labs-recent', patientId],
    queryFn:  () => getRecentLabResults(patientId!, 3),
    enabled:  Boolean(patientId) && (tab === 'overview' || tab === 'labs'),
  });

  // ── Phase 3: full lab list (labs tab) ────────────────────────────────────
  const { data: labsPage, isLoading: labsLoading } = useQuery({
    queryKey: ['patient-labs-full', patientId],
    queryFn:  () => getLabResults(patientId!, { size: 50 }),
    enabled:  Boolean(patientId) && tab === 'labs',
  });
  const allLabs: LabResult[] = labsPage?.content ?? [];

  // ── Phase 3/5: current vitals (REST + live WebSocket) ────────────────────
  const { data: currentVitals, isLoading: vitalsLoading } = useQuery({
    queryKey: ['vitals-current', patientId],
    queryFn: () => getCurrentVitals(patientId!),
    enabled: Boolean(patientId) && tab === 'vitals',
    staleTime: 10_000,
  });

  // Prefer a live WebSocket snapshot, then the REST snapshot.
  const vitals: VitalsSnapshot | null = liveSnapshot ?? currentVitals ?? null;

  // ── Phase 5: patient-scoped alerts ───────────────────────────────────────
  const { data: patientAlerts = [], isLoading: alertsLoading } = useQuery({
    queryKey: ['patient-alerts', patientId],
    queryFn: () => getPatientAlerts(patientId!),
    enabled: Boolean(patientId) && tab === 'alerts',
    staleTime: 10_000,
  });

  // ── Phase 4: predictions (risks tab) ─────────────────────────────────────
  const { data: predictions = [], isLoading: predsLoading } = useQuery({
    queryKey: ['patient-predictions', patientId],
    queryFn:  () => getPatientPredictions(patientId!),
    enabled:  Boolean(patientId) && tab === 'risks',
    staleTime: 30_000,
  });

  // ── Phase 6: active care plan (care tab) ──────────────────────────────────
  const { data: activePlan, isLoading: carePlanLoading } = useQuery({
    queryKey: ['active-care-plan', patientId],
    queryFn:  () => getActiveCarePlan(patientId!),
    enabled:  Boolean(patientId) && tab === 'care',
    retry:    false,    // 404 = no active plan; don't retry
  });

  // ── Loading state ─────────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div>
        <PageHeader title="Patient 360" subtitle="Loading…" backTo={{ to: '/patients', label: 'Back to patients' }} />
        <Card>
          <div className="space-y-3 p-4">
            <SkeletonBlock className="h-6 w-1/3" />
            <SkeletonBlock className="h-4 w-1/2" />
            <SkeletonRows rows={4} cols={3} />
          </div>
        </Card>
      </div>
    );
  }

  // ── Not found ─────────────────────────────────────────────────────────────
  if (isError || !patient) {
    const is404 = (error as any)?.response?.status === 404;
    return (
      <div>
        <PageHeader title="Patient 360" subtitle="Patient not found" backTo={{ to: '/patients', label: 'Back to patients' }} />
        <EmptyState
          title={is404 ? 'Patient not found' : 'Could not load patient'}
          description={is404
            ? 'No patient exists with this ID. They may have been deactivated or the link is incorrect.'
            : 'There was a problem loading this patient record. Please try again.'}
          action={<LinkButton to="/patients" variant="primary">Back to patients</LinkButton>}
        />
      </div>
    );
  }

  // ── Map API timeline events to UI TimelineEvent shape ─────────────────────
  const timelineItems: TimelineEvent[] = timeline.map((e) => ({
    id:        e.id,
    timestamp: e.timestamp ?? '',
    title:     e.title,
    detail:    e.detail,
    tone:      (e.tone as TimelineEvent['tone']) ?? 'neutral',
  }));


  return (
    <div>
      <PageHeader
        title="Patient 360"
        subtitle={
          <span className="flex flex-wrap items-center gap-2">
            <span className="text-base font-semibold text-slate-900">{patient.name}</span>
            <span className="font-mono-clinical text-xs text-slate-500">Patient ID: {patient.id}</span>
            <Badge tone={patient.status === 'Active' ? 'healthy' : 'warning'} dot>
              {patient.status}
            </Badge>
          </span>
        }
        backTo={{ to: '/patients', label: 'Back to patients' }}
        meta={
          <>
            <Badge tone={patient.fhirConnected ? 'healthy' : 'critical'} dot>
              {patient.fhirConnected ? 'FHIR connected' : 'FHIR connection failed'}
            </Badge>
            <Badge tone={patient.twinStatus === 'Synchronized' ? 'healthy' : 'warning'} dot>
              Digital twin {patient.twinStatus?.toLowerCase()}
            </Badge>
            <span className="flex items-center gap-1.5 text-xs text-slate-500">
              <ClockIcon className="h-3.5 w-3.5" aria-hidden="true" />
              {formatRelative(patient.updatedAt)}
            </span>
            <Badge tone={patient.consentComplete ? 'healthy' : 'warning'}>
              {patient.consentComplete ? 'Consent verified' : 'Consent missing'}
            </Badge>
          </>
        }
        actions={
          <>
            <LinkButton to={`/vitals/${patient.id}`}>Vitals</LinkButton>
            <LinkButton to={`/twins/${patient.id}`} variant="primary">View digital twin</LinkButton>
          </>
        }
      />

      {!patient.consentComplete && (
        <div className="mb-4">
          <Notice tone="warning" title="Patient consent missing" action={<LinkButton to="/consent" size="sm">Manage consent</LinkButton>}>
            AI risk analysis and wearable ingestion are disabled for this patient until consent is recorded.
          </Notice>
        </div>
      )}

      <Card>
        <Tabs tabs={tabs} active={tab} onChange={setTab} className="px-2" />

        {/* ── Overview ── */}
        {tab === 'overview' && (
          <div className="grid grid-cols-1 gap-4 p-4 lg:grid-cols-3">
            <div className="space-y-4 lg:col-span-2">
              <Card className="shadow-none">
                <CardHeader title="Patient information" />
                <CardBody className="pt-0">
                  <dl>
                    <DefinitionRow label="Date of birth" value={`${patient.dob ?? '—'} (${patient.age} years)`} />
                    <DefinitionRow label="Gender"            value={patient.gender ?? '—'} />
                    <DefinitionRow label="Contact"           value={patient.phone ?? patient.email ?? '—'} />
                    <DefinitionRow label="Attending provider" value={patient.providerName ?? '—'} />
                    <DefinitionRow label="FHIR resource"     value={<span className="font-mono-clinical text-xs">{patient.fhirId ?? '—'}</span>} />
                  </dl>
                </CardBody>
              </Card>
              <Card className="shadow-none">
                <CardHeader title="Recent events" description="Merged from EHR, laboratory and wearable sources" />
                <CardBody>
                  {timelineItems.length === 0
                    ? <EmptyState title="No events recorded" description="Timeline events will appear as data sources synchronise." />
                    : <Timeline items={timelineItems.slice(0, 4)} />}
                </CardBody>
              </Card>
            </div>
            <div className="space-y-4">
              <Card className="shadow-none">
                <CardHeader title="Clinical status" />
                <CardBody className="space-y-3">
                  <div>
                    <p className="text-2xs uppercase tracking-wide text-slate-500">Active conditions</p>
                    <ul className="mt-1.5 flex flex-wrap gap-1.5">
                      {patient.conditions.length === 0
                        ? <li className="text-sm text-slate-500">None recorded</li>
                        : patient.conditions.map((c) => <li key={c}><Badge tone="neutral">{c}</Badge></li>)}
                    </ul>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="rounded-md border border-slate-200 p-3">
                      <p className="text-2xs uppercase tracking-wide text-slate-500">Health status</p>
                      <p className="mt-1 text-sm font-semibold text-slate-900">{patient.healthStatus ?? '—'}</p>
                    </div>
                    <div className="rounded-md border border-slate-200 p-3">
                      <p className="text-2xs uppercase tracking-wide text-slate-500">Current risk</p>
                      <p className="mt-1">
                        <RiskBadge risk={patient.riskLevel as 'High' | 'Medium' | 'Low'} />
                      </p>
                    </div>
                  </div>
                  <ProgressBar label="Care-plan adherence" value={patient.adherence} tone={adherenceTone(patient.adherence)} />
                </CardBody>
              </Card>
              <Card className="shadow-none">
                <CardHeader title="Recent lab results" actions={<LinkButton to="/labs" size="sm" variant="ghost">All labs</LinkButton>} />
                <CardBody className="pt-0">
                  {recentLabs.length === 0 ? (
                    <EmptyState title="No lab results" description="No laboratory observations have been received for this patient via FHIR." />
                  ) : (
                    <dl>
                      {recentLabs.map((lab) => (
                        <DefinitionRow
                          key={lab.id}
                          label={lab.test}
                          value={
                            <span className="flex items-center justify-end gap-2">
                              <span className="tabular">{lab.result}</span>
                              <Badge tone={lab.status === 'Normal' ? 'healthy' : lab.status === 'Pending' ? 'neutral' : 'warning'}>
                                {lab.status}
                              </Badge>
                            </span>
                          }
                        />
                      ))}
                    </dl>
                  )}
                </CardBody>
              </Card>
            </div>
          </div>
        )}

        {/* ── Twin ── */}
        {tab === 'twin' && (
          <div className="grid grid-cols-1 gap-4 p-4 lg:grid-cols-3">
            <Card className="shadow-none lg:col-span-2">
              <CardHeader title="Digital Health Twin summary" icon={<LinkIcon className="h-4 w-4" />} />
              <CardBody>
                <dl>
                  <DefinitionRow label="Twin ID"         value={patient.twinId ?? '—'} />
                  <DefinitionRow label="Model version"   value="v2.1" />
                  <DefinitionRow label="Completeness"    value={`${patient.twinCompleteness}%`} />
                  <DefinitionRow label="Status"          value={
                    <Badge tone={patient.twinStatus === 'Synchronized' ? 'healthy' : 'warning'} dot>
                      {patient.twinStatus}
                    </Badge>
                  } />
                </dl>
                <div className="mt-4">
                  <ProgressBar label="Data completeness" value={patient.twinCompleteness} tone="info" />
                </div>
              </CardBody>
            </Card>
            <Card className="shadow-none">
              <CardHeader title="Open the full twin" />
              <CardBody className="space-y-3">
                <p className="text-sm text-slate-600">
                  The Digital Health Twin view provides the body risk heatmap, source-system breakdown and the full state timeline.
                </p>
                <LinkButton to={`/twins/${patient.id}`} variant="primary" className="w-full">
                  View Digital Health Twin
                </LinkButton>
              </CardBody>
            </Card>
          </div>
        )}

        {/* ── Vitals ── */}
        {tab === 'vitals' && (
          <div className="p-4">
            {vitalsLoading && !vitals ? (
              <SkeletonRows rows={1} cols={5} />
            ) : vitals ? (
              <>
                <div className="mb-3 flex items-center justify-between">
                  <p className="text-xs text-slate-500">
                    {vitalsStreamConnected
                      ? 'Live vital-sign stream connected'
                      : 'Showing latest persisted vital-sign snapshot'}
                  </p>
                  <div className="flex items-center gap-2 text-xs text-slate-500">
                    <StatusDot tone={vitalsStreamConnected ? 'healthy' : 'warning'} />
                    {vitalsLiveUpdated
                      ? `Updated ${formatRelative(vitalsLiveUpdated)}`
                      : formatRelative(vitals.updatedAt)}
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
                  <VitalTile label="Heart rate"       value={`${vitals.heartRate} BPM`}        tone={vitals.heartRate > 100 ? 'critical' : 'healthy'} />
                  <VitalTile label="Blood pressure"   value={`${vitals.bloodPressure} mmHg`}   tone={vitals.bloodPressure?.startsWith('14') ? 'warning' : 'healthy'} />
                  <VitalTile label="SpO₂"             value={`${vitals.spo2}%`}                tone={vitals.spo2 < 95 ? 'warning' : 'healthy'} />
                  <VitalTile label="Temperature"      value={`${vitals.temperature}°C`}        tone="healthy" />
                  <VitalTile label="Respiratory rate" value={`${vitals.respiratoryRate} /min`} tone="healthy" />
                </div>
                <LinkButton to={`/vitals/${patient.id}`} size="sm" className="mt-4">Full vitals</LinkButton>
              </>
            ) : (
              <EmptyState title="No vitals available" description="No current vital-sign snapshot is available for this patient yet." />
            )}
          </div>
        )}

        {/* ── Labs (Phase 3 — real FHIR data) ── */}
        {tab === 'labs' && (
          <div className="p-4">
            {labsLoading ? (
              <SkeletonRows rows={5} cols={5} />
            ) : allLabs.length === 0 ? (
              <EmptyState
                title="No lab results"
                description="No laboratory observations have been received for this patient via FHIR."
              />
            ) : (
              <TableShell>
                <thead>
                  <tr>
                    <Th>Test</Th>
                    <Th>Result</Th>
                    <Th>Reference range</Th>
                    <Th>Status</Th>
                    <Th>Date</Th>
                  </tr>
                </thead>
                <tbody>
                  {allLabs.map((lab) => (
                    <Tr key={lab.id}>
                      <Td className="font-medium text-slate-900">{lab.test}</Td>
                      <Td className="tabular">{lab.result}</Td>
                      <Td className="text-slate-500">{lab.referenceRange ?? '—'}</Td>
                      <Td>
                        <Badge tone={lab.status === 'Normal' ? 'healthy' : lab.status === 'Pending' ? 'neutral' : 'warning'}>
                          {lab.status}
                        </Badge>
                      </Td>
                      <Td className="text-slate-500">{lab.date}</Td>
                    </Tr>
                  ))}
                </tbody>
              </TableShell>
            )}
          </div>
        )}

        {/* ── Risks (Phase 4 — real AI predictions) ── */}
        {tab === 'risks' && (
          <div className="space-y-3 p-4">
            <AiNotice kind="prediction" />
            {predsLoading ? (
              <SkeletonRows rows={3} cols={5} />
            ) : predictions.length === 0 ? (
              <EmptyState
                title="No predictions available"
                description="AI risk predictions will appear once the prediction models have run for this patient."
              />
            ) : (
              <div className="space-y-3">
                {predictions.map((pred: Prediction) => (
                  <div
                    key={pred.id}
                    className="flex items-center justify-between rounded-lg border border-slate-200 px-4 py-3"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-slate-900">{pred.label}</p>
                      <p className="text-2xs text-slate-500">
                        {pred.model} · Round {pred.federatedRound} · Confidence {pred.confidence}%
                      </p>
                    </div>
                    <div className="ml-4 flex items-center gap-3 shrink-0">
                      <span className={`text-xl font-semibold tabular ${
                        pred.category === 'High' ? 'text-critical-600'
                        : pred.category === 'Medium' ? 'text-amber-600'
                        : 'text-teal-600'
                      }`}>
                        {pred.value}%
                      </span>
                      <RiskBadge risk={pred.category} />
                      <LinkButton
                        to={`/explain?predictionId=${pred.id}`}
                        size="sm"
                        variant="ghost"
                      >
                        Explain
                      </LinkButton>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ── Alerts ── */}
        {tab === 'alerts' && (
          <div className="p-4">
            {alertsLoading ? (
              <SkeletonRows rows={4} cols={6} />
            ) : patientAlerts.length === 0 ? (
              <EmptyState
                title="No alerts for this patient"
                description="No patient-scoped alerts are currently recorded. New streaming anomalies will appear here automatically."
                action={<LinkButton to="/alerts" size="sm" variant="primary">Open alerts</LinkButton>}
              />
            ) : (
              <TableShell>
                <thead>
                  <tr>
                    <Th>Severity</Th>
                    <Th>Event</Th>
                    <Th>AI analysis</Th>
                    <Th>Detected</Th>
                    <Th>Status</Th>
                    <Th>Provider</Th>
                  </tr>
                </thead>
                <tbody>
                  {patientAlerts.map((alert) => (
                    <Tr key={alert.id}>
                      <Td><Badge tone={alert.severity === 'HIGH' ? 'critical' : alert.severity === 'MEDIUM' ? 'warning' : 'info'}>{alert.severity}</Badge></Td>
                      <Td className="font-medium text-slate-900">{alert.event}</Td>
                      <Td className="max-w-sm text-xs text-slate-500">{alert.analysis ?? '—'}</Td>
                      <Td className="whitespace-nowrap text-slate-500">{formatRelative(alert.detectedAt)}</Td>
                      <Td><Badge tone={alert.status === 'Unacknowledged' ? 'critical' : alert.status === 'Escalated' ? 'warning' : alert.status === 'Resolved' ? 'healthy' : 'info'}>{alert.status}</Badge></Td>
                      <Td className="whitespace-nowrap text-slate-600">{alert.assignedProvider ?? '—'}</Td>
                    </Tr>
                  ))}
                </tbody>
              </TableShell>
            )}
          </div>
        )}

        {/* ── Care Plan (Phase 6) ── */}
        {tab === 'care' && (
          <div className="p-4">
            {carePlanLoading ? (
              <div className="space-y-3">
                <div className="h-4 w-1/2 animate-pulse rounded bg-slate-200" />
                <div className="h-4 w-3/4 animate-pulse rounded bg-slate-200" />
                <div className="h-4 w-2/3 animate-pulse rounded bg-slate-200" />
              </div>
            ) : activePlan ? (
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-semibold text-slate-900">{activePlan.goal}</p>
                    <p className="mt-0.5 text-xs text-slate-500">
                      Plan {activePlan.id} · {activePlan.riskLevel} risk · {activePlan.adherence}% adherence
                    </p>
                  </div>
                  <LinkButton to={`/care-plans/${activePlan.id}/adherence`} size="sm">
                    Track adherence
                  </LinkButton>
                </div>
                {activePlan.recommendations.slice(0, 2).map((rec) => (
                  <div key={rec.id} className="rounded-md border border-slate-200 p-3">
                    <p className="text-sm font-medium text-slate-800">{rec.title}</p>
                    <p className="mt-0.5 text-xs text-slate-600">{rec.intervention}</p>
                  </div>
                ))}
                <div className="flex gap-2 pt-1">
                  <LinkButton to={`/care-plans/${activePlan.id}/review`} size="sm" variant="primary">
                    Review plan
                  </LinkButton>
                  <LinkButton to={`/care-plans/${activePlan.id}/edit`} size="sm">
                    Edit plan
                  </LinkButton>
                </div>
              </div>
            ) : (
              <EmptyState
                title="No active care plan"
                description="Generate an AI-assisted care plan from this patient's twin state and current risk profile."
                action={
                  <LinkButton to={`/care-plans/new?patientId=${patient.id}`} variant="primary" size="sm">
                    Create care plan
                  </LinkButton>
                }
              />
            )}
          </div>
        )}
      </Card>
    </div>
  );
}

// ── Sub-components ────────────────────────────────────────────────────────

function VitalTile({ label, value, tone }: { label: string; value: string; tone: 'healthy' | 'warning' | 'critical' }) {
  return (
    <div className="rounded-lg border border-slate-200 p-3">
      <div className="flex items-center justify-between">
        <p className="text-2xs uppercase tracking-wide text-slate-500">{label}</p>
        <StatusDot tone={tone} />
      </div>
      <p className="mt-1.5 text-lg font-semibold tabular text-slate-900">{value}</p>
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
