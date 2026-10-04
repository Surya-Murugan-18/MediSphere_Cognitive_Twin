import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { BellRingIcon, RadioIcon, TimerIcon, WatchIcon } from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader';
import { KpiCard } from '../components/ui/KpiCard';
import { Card, CardBody, CardHeader, DefinitionRow } from '../components/ui/Card';
import { Badge, StatusDot } from '../components/ui/Badge';
import { LinkButton } from '../components/ui/Button';
import { TableShell, Td, Th, Tr } from '../components/ui/Table';
import { SkeletonRows } from '../components/ui/States';
import { getPatients } from '../api/patients';
import { getMonitoringStats, getKafkaStats, getKafkaEvents } from '../api/monitoring';
import { useMonitoringStream } from '../hooks/useMonitoringStream';
import { useKafkaEventStream } from '../hooks/useKafkaEventStream';

export function Monitoring() {
  const navigate = useNavigate();

  // ── REST API queries ───────────────────────────────────────────────────

  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['monitoring-stats'],
    queryFn:  getMonitoringStats,
    staleTime: 30_000,
    refetchInterval: 30_000,
  });

  const { data: kafkaStats } = useQuery({
    queryKey: ['kafka-stats'],
    queryFn:  getKafkaStats,
    staleTime: 15_000,
    refetchInterval: 15_000,
  });

  // Initial Kafka events from REST (pre-populate the panel before WS kicks in)
  const { data: initialEvents } = useQuery({
    queryKey: ['kafka-events-initial'],
    queryFn:  () => getKafkaEvents(10),
    staleTime: 60_000,
  });

  // Active patient list for the monitoring table (initial load)
  const { data: patientsPage, isLoading: patientsLoading } = useQuery({
    queryKey: ['patients', { status: 'Active' }],
    queryFn:  () => getPatients({ status: 'Active' }, 0, 50),
    staleTime: 60_000,
  });

  const loading = statsLoading || patientsLoading;
  const monitored = patientsPage?.content ?? [];

  // ── WebSocket streams ──────────────────────────────────────────────────

  // Live vital updates for each patient row — replaces the setInterval jitter
  const { liveVitals, isConnected } = useMonitoringStream();

  // Live Kafka event feed
  const { events: liveKafkaEvents } = useKafkaEventStream();

  // Merge live WebSocket data with initial Kafka events (WS events prepended)
  const kafkaEventDisplay = liveKafkaEvents.length > 0
    ? liveKafkaEvents
    : (initialEvents ?? []);

  // ── Derive KPI values from API or fallback ─────────────────────────────

  const alertsToday     = stats?.alertsToday    ?? 0;
  const wearablesOnline = stats?.wearablesOnline ?? 0;
  const avgResponse     = stats?.avgResponseMin  ?? 0;
  const streamStatus    = isConnected ? 'Active' : (stats?.streamStatus ?? 'Idle');

  const eventsPerSec   = kafkaStats?.eventsPerSec ?? 0;
  const consumerLag    = kafkaStats?.consumerLag  ?? '0 ms';

  return (
    <div>
      <PageHeader
        title="Real-time health surveillance"
        subtitle="Continuous vitals streaming and anomaly detection across monitored patients"
        meta={
          <>
            <span className="flex items-center gap-1.5 text-xs text-slate-600">
              <StatusDot tone={isConnected ? 'healthy' : 'warning'} pulse={isConnected} />
              {isConnected ? 'Live stream active' : 'Connecting…'}
            </span>
            <Badge tone="info">Kafka · vitals.raw</Badge>
          </>
        }
        actions={<LinkButton to="/alerts" variant="primary">Clinical alerts</LinkButton>}
      />

      <section aria-label="Monitoring indicators" className="grid grid-cols-2 gap-3 xl:grid-cols-4">
        <KpiCard
          label="Alerts Today"
          value={String(alertsToday)}
          caption={alertsToday === 1 ? '1 alert today' : `${alertsToday} alerts today`}
          tone="critical"
          icon={<BellRingIcon className="h-4 w-4" />}
          to="/alerts"
        />
        <KpiCard
          label="Wearables Online"
          value={String(wearablesOnline)}
          caption="Active devices streaming"
          tone="warning"
          icon={<WatchIcon className="h-4 w-4" />}
        />
        <KpiCard
          label="Average Response"
          value={`${avgResponse} min`}
          caption="Across acknowledged alerts"
          tone="healthy"
          icon={<TimerIcon className="h-4 w-4" />}
        />
        <KpiCard
          label="Live Stream Status"
          value={streamStatus}
          caption={eventsPerSec > 0 ? `${eventsPerSec} events/sec` : 'Awaiting data'}
          tone="healthy"
          icon={<RadioIcon className="h-4 w-4" />}
          to="/status"
        />
      </section>

      <div className="mt-5 grid grid-cols-1 gap-4 xl:grid-cols-3">
        {/* ── Live patient monitoring table ── */}
        <Card className="xl:col-span-2">
          <CardHeader
            title="Live patient monitoring"
            description="Values refresh as new events arrive on the vitals stream"
            actions={
              <span className="flex items-center gap-1.5 text-2xs font-medium uppercase tracking-wide text-teal-700">
                <StatusDot tone={isConnected ? 'healthy' : 'neutral'} pulse={isConnected} />
                {isConnected ? 'Live' : 'REST'}
              </span>
            }
          />

          {loading ? (
            <SkeletonRows rows={5} cols={6} />
          ) : (
            <TableShell>
              <thead>
                <tr>
                  <Th>Patient</Th>
                  <Th align="right">Heart rate</Th>
                  <Th align="right">SpO₂</Th>
                  <Th align="right">Blood pressure</Th>
                  <Th>Status</Th>
                  <Th>Last update</Th>
                </tr>
              </thead>
              <tbody>
                {monitored.map((patient) => {
                  // Prefer live WebSocket data; fall back to REST snapshot if available
                  const live = liveVitals.get(patient.id);
                  const patientVitals = (patient as any).vitals;
                  const hr  = live?.heartRate       ?? patientVitals?.heartRate       ?? 0;
                  const sp  = live?.spo2            ?? patientVitals?.spo2            ?? 0;
                  const bp  = live?.bloodPressure   ?? patientVitals?.bloodPressure   ?? '—';
                  const offline = !live && patient.wearableStatus === 'Offline';
                  const lastUpdate = live ? 'Just now' : 'REST data';
                  const critical = hr > 120;
                  const warning  = !critical && (sp < 95 || bp.startsWith('14'));

                  return (
                    <Tr key={patient.id} onClick={() => navigate(`/patients/${patient.id}`)}>
                      <Td>
                        <span className="font-medium text-slate-900">{patient.name}</span>
                        <span className="ml-2 font-mono-clinical text-2xs text-slate-400">{patient.id}</span>
                      </Td>
                      <Td align="right" className={`tabular ${critical ? 'font-semibold text-critical-600' : ''}`}>
                        {offline ? '—' : hr}
                      </Td>
                      <Td align="right" className="tabular">
                        {offline ? '—' : `${sp}%`}
                      </Td>
                      <Td align="right" className="tabular">
                        {offline ? '—' : bp}
                      </Td>
                      <Td>
                        {offline ? (
                          <Badge tone="neutral" dot>Device offline</Badge>
                        ) : critical ? (
                          <Badge tone="critical" dot>Critical</Badge>
                        ) : warning ? (
                          <Badge tone="warning" dot>Watch</Badge>
                        ) : (
                          <Badge tone="healthy" dot>Normal</Badge>
                        )}
                      </Td>
                      <Td className="text-slate-500">{offline ? '42 min ago' : lastUpdate}</Td>
                    </Tr>
                  );
                })}
              </tbody>
            </TableShell>
          )}
        </Card>

        {/* ── Kafka stream panel ── */}
        <div className="space-y-4">
          <Card>
            <CardHeader
              title="Kafka stream"
              actions={
                <Badge tone={isConnected ? 'healthy' : 'neutral'} dot>
                  {isConnected ? 'Connected' : 'Connecting'}
                </Badge>
              }
            />
            <CardBody className="pt-0">
              <dl>
                <DefinitionRow
                  label="Kafka status"
                  value={<Badge tone={isConnected ? 'healthy' : 'neutral'} dot>{isConnected ? 'Connected' : 'Connecting'}</Badge>}
                />
                <DefinitionRow label="Events / sec" value={<span className="tabular">{eventsPerSec}</span>} />
                <DefinitionRow label="Consumer lag" value={consumerLag} />
                <DefinitionRow
                  label="Last event"
                  value={
                    <span className="font-mono-clinical text-xs">
                      {kafkaEventDisplay[0]?.time ?? '—'}
                    </span>
                  }
                />
              </dl>
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Latest events" description="Tail of vitals.raw and vitals.anomaly" />
            <ul className="divide-y divide-slate-100">
              {kafkaEventDisplay.slice(0, 5).map((event) => (
                <li key={event.id} className="px-4 py-2">
                  <p className="flex items-baseline gap-2">
                    <span className="font-mono-clinical text-2xs text-slate-400">{event.time}</span>
                    <span className="font-mono-clinical text-2xs text-brand-600">{event.topic}</span>
                  </p>
                  <p className="font-mono-clinical text-2xs text-slate-600">{event.text}</p>
                </li>
              ))}
              {kafkaEventDisplay.length === 0 && (
                <li className="px-4 py-3 text-xs text-slate-400">Awaiting events…</li>
              )}
            </ul>
          </Card>
        </div>
      </div>
    </div>
  );
}
