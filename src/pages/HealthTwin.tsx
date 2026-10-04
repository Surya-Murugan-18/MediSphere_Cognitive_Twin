import React, { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '../components/ui/PageHeader';
import { Card, CardBody, CardHeader, DefinitionRow } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { LinkButton } from '../components/ui/Button';
import { Select } from '../components/ui/Field';
import { ProgressBar } from '../components/ui/Progress';
import { Timeline } from '../components/ui/Timeline';
import { EmptyState, Notice, SkeletonBlock } from '../components/ui/States';
import { BodyMap } from '../components/twin/BodyMap';
import type { BodyRegion as BodyMapRegion } from '../components/twin/BodyMap';
import { DemoDataNote } from '../components/ui/AiNotice';
import { getPatient, getPatients } from '../api/patients';
import { getCurrentVitals } from '../api/vitals';
import { getRecentLabResults } from '../api/labs';
import { getPatientPredictions } from '../api/predictions';
import { getPatientAlerts } from '../api/alerts';
import { getTwin, getTwinBodyRegions, getTwinDataSources, getTwinTimeline } from '../api/twins';
import { getActiveCarePlan } from '../api/carePlans';
import { useVitalsStream } from '../hooks/useVitalsStream';
import { useAlertStream } from '../hooks/useAlertStream';
import type { TimelineEvent } from '../types/clinical';

const toBodyMapLevel = (r: string): 'high' | 'medium' | 'low' => {
  if (r === 'high') return 'high';
  if (r === 'medium') return 'medium';
  return 'low';
};

export function HealthTwin() {
  const { patientId } = useParams<{ patientId: string }>();
  const navigate = useNavigate();

  // The sidebar route is /twins. Resolve it to a real patient instead of
  // rendering the old "Patient not found" placeholder with no route param.
  const { data: patientsPage, isLoading: patientsLoading } = useQuery({
    queryKey: ['patients', {}, 0, 100],
    queryFn: () => getPatients({}, 0, 100),
    staleTime: 2 * 60 * 1000,
  });
  const patientList = patientsPage?.content ?? [];
  const resolvedPatientId = patientId ?? patientList[0]?.id;

  useEffect(() => {
    if (!patientId && patientList.length > 0) {
      navigate(`/twins/${patientList[0].id}`, { replace: true });
    }
  }, [patientId, patientList, navigate]);

  const { data: patient, isLoading: patientLoading, isError: patientError } = useQuery({
    queryKey: ['patient', resolvedPatientId],
    queryFn: () => getPatient(resolvedPatientId!),
    enabled: Boolean(resolvedPatientId),
    retry: (count, err: any) => err?.response?.status !== 404 && count < 2,
  });

  const { data: activeCarePlan, isLoading: carePlanLoading } = useQuery({
    queryKey: ['active-care-plan', resolvedPatientId],
    queryFn: () => getActiveCarePlan(resolvedPatientId!),
    enabled: Boolean(resolvedPatientId),
    retry: (count, err: any) => err?.response?.status !== 404 && count < 2,
  });

  const twinId = patient?.twinId;
  const { isConnected: vitalsStreamConnected, liveSnapshot } = useVitalsStream(resolvedPatientId);
  useAlertStream();

  const { data: twin, isLoading: twinLoading } = useQuery({
    queryKey: ['twin', twinId],
    queryFn: () => getTwin(twinId!),
    enabled: Boolean(twinId),
  });

  const { data: bodyRegions = [] } = useQuery({
    queryKey: ['twin-body-regions', twinId],
    queryFn: () => getTwinBodyRegions(twinId!),
    enabled: Boolean(twinId),
  });

  const { data: dataSources } = useQuery({
    queryKey: ['twin-data-sources', twinId],
    queryFn: () => getTwinDataSources(twinId!),
    enabled: Boolean(twinId),
    refetchInterval: vitalsStreamConnected ? 15_000 : 30_000,
  });

  const { data: timelineRaw = [] } = useQuery({
    queryKey: ['twin-timeline', twinId],
    queryFn: () => getTwinTimeline(twinId!),
    enabled: Boolean(twinId),
  });

  const { data: currentVitals } = useQuery({
    queryKey: ['vitals-current', resolvedPatientId],
    queryFn: () => getCurrentVitals(resolvedPatientId!),
    enabled: Boolean(resolvedPatientId),
    staleTime: 10_000,
  });

  const { data: recentLabs = [], isLoading: labsLoading } = useQuery({
    queryKey: ['patient-labs-recent', resolvedPatientId, 'twin'],
    queryFn: () => getRecentLabResults(resolvedPatientId!, 3),
    enabled: Boolean(resolvedPatientId),
    staleTime: 30_000,
  });

  const { data: predictions = [], isLoading: predictionsLoading } = useQuery({
    queryKey: ['patient-predictions', resolvedPatientId, 'twin'],
    queryFn: () => getPatientPredictions(resolvedPatientId!),
    enabled: Boolean(resolvedPatientId),
    staleTime: 30_000,
  });

  const { data: alerts = [], isLoading: alertsLoading } = useQuery({
    queryKey: ['patient-alerts', resolvedPatientId, 'twin'],
    queryFn: () => getPatientAlerts(resolvedPatientId!),
    enabled: Boolean(resolvedPatientId),
    staleTime: 10_000,
  });

  const mappedRegions: BodyMapRegion[] | undefined = bodyRegions.length > 0
    ? bodyRegions.map((r, i) => {
        const coords: [number, number, number][] = [
          [88, 118, 16],
          [112, 150, 14],
          [92, 166, 13],
          [118, 182, 11],
          [118, 112, 12],
        ];
        const [cx, cy, rad] = coords[i] ?? [100, 150, 12];
        return {
          id: r.region,
          label: r.label,
          detail: r.detail,
          level: toBodyMapLevel(r.riskLevel),
          cx,
          cy,
          r: rad,
        };
      })
    : undefined;

  const timelineItems: TimelineEvent[] = timelineRaw.map((e) => ({
    id: e.id,
    timestamp: e.timestamp ?? '',
    title: e.title,
    detail: e.detail,
    tone: (e.tone as TimelineEvent['tone']) ?? 'neutral',
  }));

  const sourceRow = (
    label: string,
    entry?: { connected: boolean; lastSync?: string | null },
  ) => ({
    label,
    tone: (entry?.connected ? 'healthy' : 'warning') as 'healthy' | 'warning',
    value: entry?.connected
      ? entry.lastSync ? `Synced ${formatRelative(entry.lastSync)}` : 'Connected'
      : 'Not connected',
  });

  const twinStatus = twin?.status ?? patient?.twinStatus ?? 'Not Created';
  const vitals = liveSnapshot ?? currentVitals ?? null;

  if (patientsLoading || patientLoading || (resolvedPatientId && !patient && !patientError)) {
    return (
      <div>
        <PageHeader title="Digital Health Twin" subtitle="Loading…" />
        <div className="space-y-3">
          <SkeletonBlock className="h-64" />
          <SkeletonBlock className="h-32" />
        </div>
      </div>
    );
  }

  if (patientError || !patient) {
    return (
      <div>
        <PageHeader
          title="Digital Health Twin"
          subtitle="Patient not found"
          backTo={{ to: '/twins', label: 'Back to twins' }}
        />
        <EmptyState
          title="Patient not found"
          description="No patient matches this ID. Use the selector to choose a patient."
          action={<LinkButton to="/patients" variant="primary">Go to patients</LinkButton>}
        />
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Digital Health Twin"
        subtitle={`${patient.name} · Twin ${twinId ?? '—'} · sourced from EHR, laboratory, wearable and vitals streams`}
        backTo={{ to: `/patients/${patient.id}`, label: 'Back to Patient 360' }}
        meta={
          <>
            <Badge tone={twinStatus === 'Synchronized' ? 'healthy' : 'warning'} dot>{twinStatus}</Badge>
            <Badge tone="info">Model {twin?.modelVersion ?? '—'}</Badge>
            <Badge tone={patient.consentComplete ? 'healthy' : 'warning'}>
              {patient.consentComplete ? 'Consent verified' : 'Consent missing'}
            </Badge>
          </>
        }
        actions={
          <div className="flex items-center gap-2">
            <label htmlFor="twin-patient" className="text-xs text-slate-500">Patient</label>
            <Select
              id="twin-patient"
              value={patient.id}
              onChange={(e) => navigate(`/twins/${e.target.value}`)}
              className="h-9 w-48"
            >
              {patientList.map((p) => (
                <option key={p.id} value={p.id}>{p.name} ({p.id})</option>
              ))}
            </Select>
          </div>
        }
      />

      {twinStatus === 'Not Created' ? (
        <Card>
          <EmptyState
            title="No digital twin for this patient"
            description="A Digital Health Twin is created once EHR access consent is granted and the first FHIR bundle is ingested."
            action={<LinkButton to="/consent" size="sm" variant="primary">Review consent</LinkButton>}
          />
        </Card>
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
            <Card className="xl:col-span-2">
              <CardHeader
                title="Twin risk heatmap"
                description="Interactive body model showing where current risk is concentrated"
              />
              <CardBody>
                {twinLoading ? <SkeletonBlock className="h-64" /> : <BodyMap regions={mappedRegions} />}
                <DemoDataNote className="mt-4" />
              </CardBody>
            </Card>

            <div className="space-y-4">
              <Card>
                <CardHeader title="Health twin status" />
                <CardBody className="pt-0">
                  <dl>
                    <DefinitionRow label="Twin ID" value={twinId ?? '—'} />
                    <DefinitionRow label="Model version" value={twin?.modelVersion ?? '—'} />
                    <DefinitionRow label="Last updated" value={formatRelative(twin?.lastUpdated ?? patient.updatedAt)} />
                    <DefinitionRow label="Completeness" value={`${twin?.completeness ?? 0}%`} />
                    <DefinitionRow label="Status" value={<Badge tone={twinStatus === 'Synchronized' ? 'healthy' : 'warning'} dot>{twinStatus}</Badge>} />
                  </dl>
                  <div className="mt-3">
                    <ProgressBar value={twin?.completeness ?? 0} tone="info" label="Data completeness" />
                  </div>
                </CardBody>
              </Card>

              <Card>
                <CardHeader title="Data sources" />
                <CardBody className="space-y-2 pt-0">
                  {[
                    sourceRow('Hospital EHR (FHIR R4)', dataSources?.ehr),
                    sourceRow('Laboratory system', dataSources?.lab),
                    sourceRow('Wearable device', dataSources?.wearable),
                    sourceRow('Vital signs stream (Kafka)', dataSources?.kafka),
                  ].map((src) => (
                    <div key={src.label} className="flex items-center justify-between gap-3 border-b border-slate-100 py-2 last:border-0">
                      <span className="text-sm text-slate-700">{src.label}</span>
                      <Badge tone={src.tone} dot>{src.value}</Badge>
                    </div>
                  ))}
                </CardBody>
              </Card>
            </div>
          </div>

          <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2 xl:grid-cols-4">
            <Card>
              <CardHeader title="Vitals" />
              <CardBody className="pt-0">
                {vitals ? (
                  <dl>
                    <DefinitionRow label="Heart rate" value={`${vitals.heartRate} BPM`} />
                    <DefinitionRow label="Blood pressure" value={`${vitals.bloodPressure} mmHg`} />
                    <DefinitionRow label="SpO₂" value={`${vitals.spo2}%`} />
                    <DefinitionRow label="Temperature" value={`${vitals.temperature}°C`} />
                    <DefinitionRow label="Respiratory rate" value={`${vitals.respiratoryRate} /min`} />
                  </dl>
                ) : (
                  <EmptyState title="Awaiting vitals" description="No current vital-sign snapshot is available for this patient." />
                )}
                <div className="mt-3 flex items-center gap-2 text-xs text-slate-500">
                  <Badge tone={vitalsStreamConnected ? 'healthy' : 'warning'} dot>
                    {vitalsStreamConnected ? 'LIVE' : 'REST snapshot'}
                  </Badge>
                </div>
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="Laboratory" />
              <CardBody className="pt-0">
                {labsLoading ? (
                  <SkeletonBlock className="h-24" />
                ) : recentLabs.length === 0 ? (
                  <EmptyState title="No labs" description="No laboratory observations are currently available for this patient." />
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {recentLabs.map((lab) => (
                      <li key={lab.id} className="py-2 first:pt-0">
                        <div className="flex items-center justify-between gap-2">
                          <span className="text-sm font-medium text-slate-900">{lab.test}</span>
                          <Badge tone={lab.status === 'Normal' ? 'healthy' : lab.status === 'Pending' ? 'neutral' : 'warning'}>{lab.status}</Badge>
                        </div>
                        <p className="mt-0.5 text-xs text-slate-500">{lab.result} · {lab.date}</p>
                      </li>
                    ))}
                  </ul>
                )}
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="Risks" />
              <CardBody className="pt-0">
                {predictionsLoading ? (
                  <SkeletonBlock className="h-24" />
                ) : predictions.length === 0 ? (
                  <EmptyState title="No predictions" description="No AI risk predictions are currently available for this patient." />
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {predictions.slice(0, 3).map((pred) => (
                      <li key={pred.id} className="py-2 first:pt-0">
                        <div className="flex items-center justify-between gap-2">
                          <span className="truncate text-sm font-medium text-slate-900">{pred.label}</span>
                          <Badge tone={pred.category === 'High' ? 'critical' : pred.category === 'Medium' ? 'warning' : 'healthy'}>{pred.category}</Badge>
                        </div>
                        <p className="mt-0.5 text-xs text-slate-500">{pred.value}% · Confidence {pred.confidence}%</p>
                      </li>
                    ))}
                  </ul>
                )}
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="Medications" />
              <CardBody className="pt-0">
                {patient.medications && patient.medications.length > 0 ? (
                  <ul className="divide-y divide-slate-100">
                    {patient.medications.map((med) => (
                      <li key={med.name} className="py-2">
                        <p className="text-sm font-medium text-slate-900">
                          {med.name} <span className="font-normal text-slate-500">{med.dose}</span>
                        </p>
                        <p className="text-xs text-slate-500">{med.frequency}</p>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <EmptyState title="No medications" description="No medication records are currently available from the patient response." />
                )}
              </CardBody>
            </Card>
          </div>

          <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
            <Card>
              <CardHeader title="Recent alerts" actions={<LinkButton to={`/alerts?patientId=${patient.id}`} size="sm" variant="ghost">All alerts</LinkButton>} />
              <CardBody className="pt-0">
                {alertsLoading ? (
                  <SkeletonBlock className="h-24" />
                ) : alerts.length === 0 ? (
                  <EmptyState title="No alerts" description="No patient-scoped alerts are currently recorded." />
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {alerts.slice(0, 4).map((alert) => (
                      <li key={alert.id} className="py-2 first:pt-0">
                        <div className="flex items-center justify-between gap-2">
                          <span className="truncate text-sm font-medium text-slate-900">{alert.event}</span>
                          <Badge tone={alert.severity === 'HIGH' ? 'critical' : alert.severity === 'MEDIUM' ? 'warning' : 'info'}>{alert.severity}</Badge>
                        </div>
                        <p className="mt-0.5 text-xs text-slate-500">{alert.status} · {formatRelative(alert.detectedAt)}</p>
                      </li>
                    ))}
                  </ul>
                )}
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="Care plan" />
              <CardBody className="pt-0">
                {carePlanLoading ? (
                  <SkeletonBlock className="h-24" />
                ) : activeCarePlan ? (
                  <div className="space-y-3">
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-sm font-medium text-slate-800">{activeCarePlan.goal}</span>
                      <Badge tone="healthy">Active</Badge>
                    </div>
                    <p className="text-xs text-slate-500">
                      Plan {activeCarePlan.id} · {activeCarePlan.riskLevel} risk
                    </p>
                    <div className="flex gap-2">
                      <LinkButton to={`/care-plans/${activeCarePlan.id}/review`} size="sm">View plan</LinkButton>
                      <LinkButton to={`/care-plans/${activeCarePlan.id}/adherence`} size="sm" variant="ghost">Track adherence</LinkButton>
                    </div>
                  </div>
                ) : (
                  <EmptyState
                    title="No care plan"
                    description="Create an AI-assisted plan from the current twin state."
                    action={<LinkButton to={`/care-plans/new?patientId=${patient.id}`} size="sm" variant="primary">Create care plan</LinkButton>}
                  />
                )}
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="Twin state timeline" description="How this patient's health state has changed" />
              <CardBody>
                {timelineItems.length === 0
                  ? <EmptyState title="No events yet" description="Events appear as data sources sync." />
                  : <Timeline items={timelineItems} />}
              </CardBody>
            </Card>
          </div>

          <div className="mt-4">
            <Notice tone="info" title="Privacy-preserving intelligence">
              Twin data remains within this hospital. Only model updates are shared with the federated learning network.
            </Notice>
          </div>
        </>
      )}
    </div>
  );
}

function formatRelative(ts?: string | null): string {
  if (!ts) return '—';
  try {
    const diff = Date.now() - new Date(ts).getTime();
    const mins = Math.floor(diff / 60_000);
    if (mins < 1) return 'Just now';
    if (mins < 60) return `${mins} min ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return `${hrs} h ago`;
    return `${Math.floor(hrs / 24)}d ago`;
  } catch {
    return '—';
  }
}
