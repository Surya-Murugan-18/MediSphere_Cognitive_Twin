import React, { useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { PageHeader } from '../components/ui/PageHeader';
import { Card, CardBody, CardHeader, DefinitionRow } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button, LinkButton } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { TrendChart } from '../components/charts/TrendChart';
import { AiNotice } from '../components/ui/AiNotice';
import { SkeletonBlock } from '../components/ui/States';
import { getAlert, acknowledgeAlert, escalateAlert, resolveAlert } from '../api/alerts';
import { getPatient } from '../api/patients';
import { getVitalsHistory } from '../api/vitals';
import { getPatientPredictions } from '../api/predictions';

const severityTone = { HIGH: 'critical', MEDIUM: 'warning', LOW: 'info' } as const;

export function AlertDetails() {
  const { alertId } = useParams<{ alertId: string }>();
  const queryClient = useQueryClient();
  const [escalateOpen, setEscalateOpen] = useState(false);

  // ── Load alert ─────────────────────────────────────────────────────────

  const { data: alert, isLoading, isError } = useQuery({
    queryKey: ['alert', alertId],
    queryFn:  () => getAlert(alertId!),
    enabled:  Boolean(alertId),
    staleTime: 30_000,
  });

  // ── Load patient + predictions context ────────────────────────────────

  const { data: patient } = useQuery({
    queryKey: ['patient', alert?.patientId],
    queryFn:  () => getPatient(alert!.patientId),
    enabled:  Boolean(alert?.patientId),
    staleTime: 5 * 60_000,
  });

  const { data: predictions } = useQuery({
    queryKey: ['patient-predictions', alert?.patientId],
    queryFn:  () => getPatientPredictions(alert!.patientId),
    enabled:  Boolean(alert?.patientId),
    staleTime: 5 * 60_000,
  });

  // HR trend chart around detectedAt (unused variable removed)

  const { data: hrHistory } = useQuery({
    queryKey: ['vitals-history-alert', alert?.patientId, alert?.detectedAt],
    queryFn:  () => getVitalsHistory(alert!.patientId, 'heartRate', '24h'),
    enabled:  Boolean(alert?.patientId),
    staleTime: 5 * 60_000,
  });

  // ── Mutations ─────────────────────────────────────────────────────────

  const invalidateAlerts = () => {
    queryClient.invalidateQueries({ queryKey: ['alert', alertId] });
    queryClient.invalidateQueries({ queryKey: ['alerts'] });
    queryClient.invalidateQueries({ queryKey: ['alert-count'] });
  };

  const acknowledgeMutation = useMutation({
    mutationFn: () => acknowledgeAlert(alertId!),
    onSuccess: () => {
      invalidateAlerts();
      toast.success('Alert acknowledged', {
        description: `${alertId} recorded in the audit trail.`,
      });
    },
    onError: (err: unknown) => {
      const msg = err instanceof Error ? err.message : 'Failed to acknowledge alert';
      toast.error(msg);
    },
  });

  const escalateMutation = useMutation({
    mutationFn: () => escalateAlert(alertId!, 'Requires specialist review', 'On-call cardiology'),
    onSuccess: (updated) => {
      invalidateAlerts();
      setEscalateOpen(false);
      toast.warning('Alert escalated to on-call cardiology', {
        description: `${updated.patientName} · ${updated.event}`,
      });
    },
    onError: (err: unknown) => {
      const msg = err instanceof Error ? err.message : 'Failed to escalate alert';
      toast.error(msg);
    },
  });

  const resolveMutation = useMutation({
    mutationFn: () => resolveAlert(alertId!, 'Reviewed and resolved by provider'),
    onSuccess: () => {
      invalidateAlerts();
      toast.success('Alert resolved');
    },
    onError: (err: unknown) => {
      const msg = err instanceof Error ? err.message : 'Failed to resolve alert';
      toast.error(msg);
    },
  });

  // ── Render states ─────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <div className="space-y-4">
        <SkeletonBlock className="h-20" />
        <SkeletonBlock className="h-64" />
      </div>
    );
  }

  if (isError || !alert) return <Navigate to="/alerts" replace />;

  const status = alert.status;
  const patientPredictions = predictions ?? [];
  const hrData = (hrHistory?.data ?? []).map(({ t, value }) => ({ t, value }));

  return (
    <div>
      <PageHeader
        title="Alert details"
        subtitle={`${alert.id} · ${alert.type} · detected ${
          alert.detectedAt
            ? new Date(alert.detectedAt).toLocaleString('en-GB', { hour12: false })
            : '—'
        }`}
        backTo={{ to: '/alerts', label: 'Back to alerts' }}
        meta={
          <>
            <Badge tone={severityTone[alert.severity]}>{alert.severity} priority</Badge>
            <Badge
              tone={
                status === 'Unacknowledged' ? 'critical'
                  : status === 'Escalated' ? 'warning'
                  : status === 'Resolved' ? 'healthy'
                  : 'info'
              }
            >
              {status}
            </Badge>
            <Badge tone="neutral">Assigned: {alert.assignedProvider ?? '—'}</Badge>
          </>
        }
        actions={
          <>
            <Button
              onClick={() => acknowledgeMutation.mutate()}
              disabled={status !== 'Unacknowledged' || acknowledgeMutation.isPending}
              variant={status === 'Unacknowledged' ? 'primary' : 'secondary'}
            >
              {acknowledgeMutation.isPending
                ? 'Acknowledging…'
                : status === 'Unacknowledged'
                ? 'Acknowledge'
                : 'Acknowledged'}
            </Button>
            <Button
              variant="danger"
              onClick={() => setEscalateOpen(true)}
              disabled={status === 'Resolved' || escalateMutation.isPending}
            >
              Escalate
            </Button>
            {status !== 'Resolved' && (
              <Button
                variant="secondary"
                onClick={() => resolveMutation.mutate()}
                disabled={resolveMutation.isPending}
              >
                {resolveMutation.isPending ? 'Resolving…' : 'Resolve'}
              </Button>
            )}
            <LinkButton to={`/patients/${alert.patientId}`}>View patient</LinkButton>
            <LinkButton to={`/twins/${alert.patientId}`}>View digital twin</LinkButton>
          </>
        }
      />

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <div className="space-y-4 xl:col-span-2">
          <Card>
            <CardHeader title="Event" description={`${alert.patientName} · ${alert.event}`} />
            <CardBody className="space-y-4">
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                <Metric label="Current value"  value={alert.currentValue  ?? '—'} tone="critical" />
                <Metric label="Previous value" value={alert.previousValue ?? '—'} tone="neutral" />
                <Metric
                  label="Detected"
                  value={alert.detectedAt
                    ? new Date(alert.detectedAt).toLocaleTimeString('en-GB', { hour12: false })
                    : '—'}
                  tone="neutral"
                />
                <Metric label="AI confidence"  value={`${alert.confidence}%`} tone="info" />
              </div>
              <AiNotice kind="alert" />
              {hrData.length > 0 && (
                <div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Heart-rate trend around detection
                  </p>
                  <TrendChart
                    data={hrData}
                    xKey="t"
                    series={[{ key: 'value', name: 'Heart rate (BPM)', color: '#d13f3f' }]}
                    domain={[50, 160]}
                    area
                    height={200}
                  />
                </div>
              )}
              <div className="rounded-md border border-slate-200 bg-slate-50 p-3">
                <p className="text-2xs font-semibold uppercase tracking-wide text-slate-500">AI analysis</p>
                <p className="mt-1 text-sm text-slate-700">{alert.analysis}</p>
              </div>
            </CardBody>
          </Card>

          {/* Alert audit trail from API response */}
          <Card>
            <CardHeader title="Alert audit trail" description="Every action on this alert is recorded" />
            <ul className="divide-y divide-slate-100">
              {alert.auditTrail.map((entry) => (
                <li key={entry.id} className="flex items-baseline gap-3 px-4 py-2.5">
                  <span className="font-mono-clinical text-2xs text-slate-400">
                    {entry.timestamp
                      ? new Date(entry.timestamp).toLocaleTimeString('en-GB', { hour12: false })
                      : '—'}
                  </span>
                  <span className="text-sm text-slate-700">{entry.action}</span>
                  <span className="ml-auto text-2xs text-slate-400">{entry.actor}</span>
                </li>
              ))}
              {alert.auditTrail.length === 0 && (
                <li className="px-4 py-3 text-xs text-slate-400">No audit entries yet.</li>
              )}
            </ul>
          </Card>
        </div>

        <div className="space-y-4">
          {/* Patient context */}
          <Card>
            <CardHeader title="Patient history" />
            <CardBody className="pt-0">
              {patient ? (
                <dl>
                  <DefinitionRow label="Patient"    value={`${patient.name} (${patient.id})`} />
                  <DefinitionRow label="DOB / gender" value={`${patient.dob} · ${patient.gender}`} />
                  <DefinitionRow label="Conditions" value={patient.conditions.join(', ')} />
                  <DefinitionRow label="Attending"  value={patient.providerName ?? '—'} />
                </dl>
              ) : (
                <p className="py-3 text-sm text-slate-500">Patient data not available.</p>
              )}
            </CardBody>
          </Card>

          {/* Digital twin snapshot */}
          <Card>
            <CardHeader title="Digital twin snapshot" />
            <CardBody className="pt-0">
              {patient ? (
                <dl>
                  <DefinitionRow label="Twin ID"      value={patient.twinId ?? '—'} />
                  <DefinitionRow label="Completeness" value={`${patient.twinCompleteness ?? 0}%`} />
                  <DefinitionRow
                    label="Status"
                    value={<Badge tone="healthy" dot>{patient.twinStatus ?? 'Unknown'}</Badge>}
                  />
                </dl>
              ) : (
                <SkeletonBlock className="h-20" />
              )}
            </CardBody>
          </Card>

          {/* Relevant predictions */}
          <Card>
            <CardHeader title="Relevant risk predictions" />
            <CardBody className="pt-0">
              {patientPredictions.length === 0 ? (
                <p className="py-3 text-sm text-slate-500">No model output available for this patient.</p>
              ) : (
                <dl>
                  {patientPredictions.map((pred) => (
                    <DefinitionRow key={pred.id} label={pred.label} value={`${pred.value}%`} />
                  ))}
                </dl>
              )}
              <LinkButton
                to={
                  patientPredictions.length > 0
                    ? `/explain?predictionId=${patientPredictions[0].id}`
                    : '/predictions'
                }
                size="sm"
                className="mt-3 w-full"
              >
                View explanation
              </LinkButton>
            </CardBody>
          </Card>
        </div>
      </div>

      {/* Escalate confirm modal */}
      <Modal
        open={escalateOpen}
        onClose={() => setEscalateOpen(false)}
        title="Escalate alert to on-call cardiology?"
        description="This notifies the on-call team immediately and is recorded in the clinical audit trail."
        footer={
          <>
            <Button onClick={() => setEscalateOpen(false)}>Cancel</Button>
            <Button
              variant="danger"
              onClick={() => escalateMutation.mutate()}
              disabled={escalateMutation.isPending}
            >
              {escalateMutation.isPending ? 'Escalating…' : 'Confirm escalation'}
            </Button>
          </>
        }
      >
        <p>
          {alert.patientName} · {alert.event}. Escalation should be used when the patient requires
          immediate assessment beyond routine review.
        </p>
      </Modal>
    </div>
  );
}

function Metric({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone: 'critical' | 'neutral' | 'info';
}) {
  const color =
    tone === 'critical' ? 'text-critical-600' : tone === 'info' ? 'text-brand-600' : 'text-slate-900';
  return (
    <div className="rounded-md border border-slate-200 p-3">
      <p className="text-2xs uppercase tracking-wide text-slate-500">{label}</p>
      <p className={`mt-1 text-lg font-semibold tabular ${color}`}>{value}</p>
    </div>
  );
}
