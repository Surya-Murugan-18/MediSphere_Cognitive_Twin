import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ActivityIcon,
  BellRingIcon,
  BrainIcon,
  ClipboardListIcon,
  DatabaseIcon,
  HeartPulseIcon,
  TimerIcon,
  UsersIcon,
  WatchIcon,
} from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader';
import { KpiCard } from '../components/ui/KpiCard';
import { Card, CardBody, CardHeader } from '../components/ui/Card';
import { Badge, RiskBadge, StatusDot } from '../components/ui/Badge';
import { Button, LinkButton } from '../components/ui/Button';
import { TableShell, Td, Th, Tr } from '../components/ui/Table';
import { RiskDonut } from '../components/charts/RiskDonut';
import { SkeletonBlock, SkeletonRows } from '../components/ui/States';
import { DemoDataNote } from '../components/ui/AiNotice';
import { getDashboardStats, getRiskDistribution } from '../api/dashboard';
import { getPatients } from '../api/patients';
import { getAlerts, getAlertCount } from '../api/alerts';
import { getAuditLogs } from '../api/audit';
import { getKafkaEvents } from '../api/monitoring';
import { useKafkaEventStream } from '../hooks/useKafkaEventStream';
import { useAuth } from '../hooks/useAuth';

const severityTone = { HIGH: 'critical', MEDIUM: 'warning', LOW: 'info' } as const;

export function Dashboard() {
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();
  const { currentUser } = useAuth();

  useEffect(() => {
    const timer = window.setTimeout(() => setLoading(false), 700);
    return () => window.clearTimeout(timer);
  }, []);

  // ── All 8 KPIs from single API call (Phase 7) ─────────────────────────
  const { data: stats } = useQuery({
    queryKey: ['dashboard-stats'],
    queryFn: getDashboardStats,
    staleTime: 60_000,
    refetchInterval: 60_000,
  });

  // ── Risk donut (Phase 7 — replaces static riskDistribution) ──────────
  const { data: riskDist } = useQuery({
    queryKey: ['dashboard-risk-distribution'],
    queryFn: getRiskDistribution,
    staleTime: 60_000,
  });

  // ── Alert count KPI (live) ────────────────────────────────────────────
  const { data: alertCountData } = useQuery({
    queryKey: ['alert-count', 'Unacknowledged'],
    queryFn: () => getAlertCount('Unacknowledged'),
    staleTime: 30_000,
    refetchInterval: 30_000,
  });
  const unackCount = alertCountData?.count ?? stats?.activeAlerts ?? 0;

  // ── Critical alerts list ──────────────────────────────────────────────
  const { data: alertsPage } = useQuery({
    queryKey: ['alerts', { status: 'Unacknowledged', size: 3 }],
    queryFn: () => getAlerts({ status: 'Unacknowledged', size: 3 }),
    staleTime: 30_000,
  });
  const openAlerts = alertsPage?.content ?? [];

  // ── High-risk patients ────────────────────────────────────────────────
  const { data: highRiskPage } = useQuery({
    queryKey: ['patients', { riskLevel: 'High', size: 4 }],
    queryFn: () => getPatients({ riskLevel: 'High' }, 0, 4),
    staleTime: 60_000,
  });
  const highRisk = highRiskPage?.content ?? [];

  // ── Recent activity from audit log (Phase 7 — replaces static recentActivity) ─
  const { data: auditPage } = useQuery({
    queryKey: ['audit-recent-activity'],
    queryFn: () => getAuditLogs({ size: 4 }),
    staleTime: 60_000,
  });
  const recentActivity = auditPage?.content ?? [];

  // ── Kafka events ──────────────────────────────────────────────────────
  const { data: initialKafkaEvents } = useQuery({
    queryKey: ['kafka-events-dashboard'],
    queryFn: () => getKafkaEvents(4),
    staleTime: 60_000,
  });
  const { events: liveKafkaEvents } = useKafkaEventStream();
  const kafkaEventDisplay = liveKafkaEvents.length > 0
    ? liveKafkaEvents.slice(0, 4)
    : (initialKafkaEvents ?? []).slice(0, 4);

  // KPI values — prefer stats API, fall back to 0
  const totalPatients     = stats?.patientsOnboarded   ?? 0;
  const fhirResources     = stats?.fhirResources        ?? 0;
  const wearablesOnline   = stats?.wearablesOnline       ?? 0;
  const wearablesOffline  = stats?.wearablesOffline      ?? 0;
  const highRiskCount     = stats?.highRiskPatients      ?? 0;
  const activePlans       = stats?.activePlans           ?? 0;
  const avgAdherence      = stats?.avgAdherence          ?? 0;
  const avgResponse       = stats?.avgAlertResponseMin   ?? 0;

  const fhirLabel = fhirResources > 1_000_000
    ? `${(fhirResources / 1_000_000).toFixed(1)}M`
    : fhirResources.toLocaleString();

  return (
    <div>
      <PageHeader
        title={`Good morning, ${currentUser?.name?.split(' ').slice(-1)[0] ?? 'Doctor'}`}
        subtitle={`MediSphere Clinical Operations Dashboard · ${currentUser?.facility ?? ''}`}
        meta={
          <>
            <Badge tone="healthy" dot>FHIR R4 connected</Badge>
            <Badge tone="healthy" dot>Kafka streaming</Badge>
            <Badge tone="info">Role: {currentUser?.role ?? '—'}</Badge>
          </>
        }
        actions={
          <>
            <LinkButton to="/monitoring">Live monitoring</LinkButton>
            <LinkButton to="/care-plans/new" variant="primary">Create care plan</LinkButton>
          </>
        }
      />

      <section aria-label="Clinical key performance indicators" className="grid grid-cols-2 gap-3 xl:grid-cols-4">
        <KpiCard label="Patients Onboarded"  value={totalPatients.toLocaleString()} caption="+34 this week" tone="info" icon={<UsersIcon className="h-4 w-4" />} to="/patients" />
        <KpiCard label="FHIR Resources"      value={fhirLabel}    caption="Lab + vitals records"            tone="neutral" icon={<DatabaseIcon className="h-4 w-4" />} to="/status" />
        <KpiCard label="Active Alerts"        value={String(unackCount)} caption={`${unackCount} unacknowledged`} tone="critical" emphasis icon={<BellRingIcon className="h-4 w-4" />} to="/alerts" />
        <KpiCard label="Wearables Online"     value={String(wearablesOnline)} caption={`${wearablesOffline} devices offline`} tone="warning" icon={<WatchIcon className="h-4 w-4" />} to="/monitoring" />
      </section>

      <section aria-label="Secondary indicators" className="mt-3 grid grid-cols-2 gap-3 xl:grid-cols-4">
        <KpiCard label="High Risk Patients"    value={String(highRiskCount)} caption="Cardiovascular-led cohort" tone="critical" icon={<HeartPulseIcon className="h-4 w-4" />} to="/predictions" />
        <KpiCard label="Active Care Plans"     value={String(activePlans)} caption={`${stats?.plansAwaitingApproval ?? 0} awaiting approval`} tone="info" icon={<ClipboardListIcon className="h-4 w-4" />} to="/care-plans" />
        <KpiCard label="Care Plan Adherence"   value={`${avgAdherence.toFixed(0)}%`} caption="+3 pts vs last month" tone="healthy" icon={<ActivityIcon className="h-4 w-4" />} to="/care-plans" />
        <KpiCard label="Average Alert Response" value={`${avgResponse.toFixed(1)} min`} caption="Target under 5 min" tone="healthy" icon={<TimerIcon className="h-4 w-4" />} to="/monitoring" />
      </section>

      <div className="mt-5 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader
            title="Critical alerts"
            description="Anomalies detected on the Kafka vitals stream awaiting clinician review"
            actions={<LinkButton to="/alerts" size="sm" variant="ghost">View all</LinkButton>}
          />
          {loading ? (
            <SkeletonRows rows={3} cols={4} />
          ) : openAlerts.length === 0 ? (
            <div className="px-4 py-6 text-sm text-slate-400 text-center">No unacknowledged alerts</div>
          ) : (
            <ul className="divide-y divide-slate-100">
              {openAlerts.map((alert) => (
                <li key={alert.id} className="flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                  <div className="flex min-w-0 items-start gap-3">
                    <Badge tone={severityTone[alert.severity]}>{alert.severity}</Badge>
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-slate-900">{alert.patientName} · {alert.event}</p>
                      <p className="mt-0.5 text-xs text-slate-500">{alert.analysis}</p>
                      <p className="mt-1 text-2xs text-slate-400">
                        {alert.detectedAt ? new Date(alert.detectedAt).toLocaleTimeString('en-GB', { hour12: false, hour: '2-digit', minute: '2-digit' }) : '—'} · {alert.status} · {alert.assignedProvider ?? '—'}
                      </p>
                    </div>
                  </div>
                  <LinkButton to={`/alerts/${alert.id}`} size="sm" variant="secondary" className="shrink-0">View alert</LinkButton>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <CardHeader title="Risk distribution" description={`Cohort of ${totalPatients.toLocaleString()} onboarded patients`} icon={<BrainIcon className="h-4 w-4" />} />
          <CardBody>
            {loading ? (
              <SkeletonBlock className="h-[200px]" />
            ) : riskDist ? (
              <RiskDonut data={riskDist.map(d => ({ ...d, tone: d.tone ?? '#94a3b8' }))} centerLabel="patients" centerValue={totalPatients.toLocaleString()} height={180} />
            ) : (
              <SkeletonBlock className="h-[180px]" />
            )}
            <DemoDataNote className="mt-3" />
          </CardBody>
        </Card>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader
            title="High-risk patients"
            description="Ranked by AI risk prediction · federated round 47"
            actions={<LinkButton to="/patients" size="sm" variant="ghost">All patients</LinkButton>}
          />
          {loading ? (
            <SkeletonRows rows={4} cols={5} />
          ) : (
            <TableShell>
              <thead>
                <tr>
                  <Th>Patient</Th>
                  <Th>Risk</Th>
                  <Th>Condition</Th>
                  <Th>Last updated</Th>
                  <Th align="right">Action</Th>
                </tr>
              </thead>
              <tbody>
                {highRisk.map((patient) => (
                  <Tr key={patient.id} onClick={() => navigate(`/patients/${patient.id}`)}>
                    <Td>
                      <span className="font-medium text-slate-900">{patient.name}</span>
                      <span className="ml-2 font-mono-clinical text-2xs text-slate-400">{patient.id}</span>
                    </Td>
                    <Td><RiskBadge risk={patient.riskLevel as any} /></Td>
                    <Td>{patient.conditions[0]}</Td>
                    <Td className="text-slate-500">{patient.updatedAt ? new Date(patient.updatedAt).toLocaleDateString('en-GB') : '—'}</Td>
                    <Td align="right"><span className="text-xs font-medium text-brand-600">View patient</span></Td>
                  </Tr>
                ))}
              </tbody>
            </TableShell>
          )}
        </Card>

        <Card>
          <CardHeader title="Real-time monitoring" description="Apache Kafka vitals pipeline" actions={<StatusDot tone="healthy" pulse />} />
          <CardBody className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-md border border-slate-200 p-3">
                <p className="text-2xs uppercase tracking-wide text-slate-500">Wearables online</p>
                <p className="mt-1 text-xl font-semibold tabular text-slate-900">{wearablesOnline || '—'}</p>
              </div>
              <div className="rounded-md border border-slate-200 p-3">
                <p className="text-2xs uppercase tracking-wide text-slate-500">Alerts today</p>
                <p className="mt-1 text-xl font-semibold tabular text-slate-900">{unackCount}</p>
              </div>
            </div>
            <div className="rounded-md border border-slate-200">
              <div className="flex items-center justify-between border-b border-slate-100 px-3 py-2">
                <p className="text-xs font-semibold text-slate-700">Latest stream events</p>
                <Badge tone="healthy" dot>Connected</Badge>
              </div>
              <ul className="divide-y divide-slate-100">
                {kafkaEventDisplay.length === 0 ? (
                  <li className="px-3 py-2 text-2xs text-slate-400">Awaiting events…</li>
                ) : kafkaEventDisplay.map((event, i) => (
                  <li key={event.id ?? i} className="flex items-baseline gap-2 px-3 py-1.5">
                    <span className="font-mono-clinical text-2xs text-slate-400">{event.time}</span>
                    <span className="font-mono-clinical text-2xs text-brand-600">{event.topic}</span>
                    <span className="truncate font-mono-clinical text-2xs text-slate-600">{event.text}</span>
                  </li>
                ))}
              </ul>
            </div>
          </CardBody>
        </Card>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader
            title="Recent clinical activity"
            description="Audited actions across your care team"
            actions={<LinkButton to="/audit" size="sm" variant="ghost">Audit log</LinkButton>}
          />
          <ul className="divide-y divide-slate-100">
            {recentActivity.length === 0 ? (
              <li className="px-4 py-4 text-sm text-slate-400 text-center">No recent activity</li>
            ) : recentActivity.map((item) => (
              <li key={item.id} className="flex items-center justify-between gap-4 px-4 py-2.5">
                <div className="flex min-w-0 items-center gap-2.5">
                  <StatusDot tone={item.status === 'Success' ? 'healthy' : item.status === 'Denied' ? 'critical' : 'info'} />
                  <p className="truncate text-sm text-slate-700">{item.action ?? '—'}</p>
                </div>
                <p className="shrink-0 text-2xs text-slate-400">
                  {item.userName ?? item.userId ?? '—'} · {item.timestamp ? new Date(item.timestamp).toLocaleTimeString('en-GB', { hour12: false }) : '—'}
                </p>
              </li>
            ))}
          </ul>
        </Card>

        <Card>
          <CardHeader title="Quick actions" />
          <CardBody className="grid grid-cols-2 gap-2">
            <Button onClick={() => navigate('/patients')} className="justify-start">
              <UsersIcon className="h-4 w-4 text-slate-400" aria-hidden="true" />
              View patients
            </Button>
            <Button onClick={() => navigate('/alerts')} className="justify-start">
              <BellRingIcon className="h-4 w-4 text-slate-400" aria-hidden="true" />
              View alerts
            </Button>
            <Button onClick={() => navigate('/predictions')} className="justify-start">
              <BrainIcon className="h-4 w-4 text-slate-400" aria-hidden="true" />
              Risk predictions
            </Button>
            <Button onClick={() => navigate('/care-plans/new')} className="justify-start">
              <ClipboardListIcon className="h-4 w-4 text-slate-400" aria-hidden="true" />
              Create care plan
            </Button>
          </CardBody>
        </Card>
      </div>
    </div>
  );
}
