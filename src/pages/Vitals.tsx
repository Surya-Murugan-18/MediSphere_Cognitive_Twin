import { useNavigate, useParams } from 'react-router-dom';
import { WatchIcon } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '../components/ui/PageHeader';
import { Card, CardBody, CardHeader, DefinitionRow } from '../components/ui/Card';
import { Badge, StatusDot } from '../components/ui/Badge';
import { LinkButton } from '../components/ui/Button';
import { Select } from '../components/ui/Field';
import { TrendChart } from '../components/charts/TrendChart';
import { Notice, SkeletonBlock } from '../components/ui/States';
import { DemoDataNote } from '../components/ui/AiNotice';
import { getCurrentVitals, getVitalsHistory, getWearableDevice } from '../api/vitals';
import { getPatients } from '../api/patients';
import { useVitalsStream } from '../hooks/useVitalsStream';

export function Vitals() {
  const { patientId: routePatientId } = useParams<{ patientId: string }>();
  const navigate = useNavigate();

  // ── Patient list for the selector dropdown ────────────────────────────
  const { data: patientsPage } = useQuery({
    queryKey: ['patients-vitals-selector'],
    queryFn:  () => getPatients({}, 0, 100),
    staleTime: 2 * 60 * 1000,
  });
  const patientList = patientsPage?.content ?? [];

  // Use first patient from list as fallback when no route param
  const patientId = routePatientId ?? patientList[0]?.id ?? '';

  // ── Phase 5 WebSocket live stream — activated ─────────────────────────
  const { liveSnapshot } = useVitalsStream(patientId || undefined);

  // ── Current vitals snapshot ───────────────────────────────────────────
  const {
    data: vitals,
    isLoading: vitalsLoading,
  } = useQuery({
    queryKey: ['vitals-current', patientId],
    queryFn:  () => getCurrentVitals(patientId),
    enabled:  Boolean(patientId),
    staleTime: 30_000,
  });

  // ── Vitals history — 3 separate queries matching the 3 chart panels ──
  const { data: hrHistory } = useQuery({
    queryKey: ['vitals-history', patientId, 'heartRate', '24h'],
    queryFn:  () => getVitalsHistory(patientId, 'heartRate', '24h'),
    enabled:  Boolean(patientId),
  });

  const { data: bpSysHistory } = useQuery({
    queryKey: ['vitals-history', patientId, 'bloodPressureSystolic', '7d'],
    queryFn:  () => getVitalsHistory(patientId, 'bloodPressureSystolic', '7d'),
    enabled:  Boolean(patientId),
  });

  const { data: bpDiaHistory } = useQuery({
    queryKey: ['vitals-history', patientId, 'bloodPressureDiastolic', '7d'],
    queryFn:  () => getVitalsHistory(patientId, 'bloodPressureDiastolic', '7d'),
    enabled:  Boolean(patientId),
  });

  const { data: spo2History } = useQuery({
    queryKey: ['vitals-history', patientId, 'spo2', '24h'],
    queryFn:  () => getVitalsHistory(patientId, 'spo2', '24h'),
    enabled:  Boolean(patientId),
  });

  // ── Wearable device ───────────────────────────────────────────────────
  const { data: device } = useQuery({
    queryKey: ['wearable-device', patientId],
    queryFn:  () => getWearableDevice(patientId),
    enabled:  Boolean(patientId),
  });

  // ── Derived display values ────────────────────────────────────────────
  const patientName = patientList.find(p => p.id === patientId)?.name ?? patientId;
  const offline = !liveSnapshot && device?.status === 'Offline';

  // Prefer live WebSocket snapshot; fall back to REST snapshot
  const heartRate       = liveSnapshot?.heartRate       ?? vitals?.heartRate       ?? 0;
  const bloodPressure   = liveSnapshot?.bloodPressure   ?? vitals?.bloodPressure   ?? '—/—';
  const spo2            = liveSnapshot?.spo2            ?? vitals?.spo2            ?? 0;
  const temperature     = liveSnapshot?.temperature     ?? vitals?.temperature     ?? 0;
  const respiratoryRate = liveSnapshot?.respiratoryRate ?? vitals?.respiratoryRate ?? 0;

  const vitalCards = [
    { label: 'Heart rate',       value: String(heartRate),       unit: 'BPM',  state: heartRate > 100 ? 'Critical' : 'Normal' },
    { label: 'Blood pressure',   value: bloodPressure,           unit: 'mmHg', state: bloodPressure.startsWith('14') ? 'Elevated' : 'Normal' },
    { label: 'SpO₂',             value: String(spo2),            unit: '%',    state: spo2 < 95 ? 'Low' : 'Normal' },
    { label: 'Temperature',      value: String(temperature),     unit: '°C',   state: 'Normal' },
    { label: 'Respiratory rate', value: String(respiratoryRate), unit: '/min', state: respiratoryRate > 20 ? 'Elevated' : 'Normal' },
  ];

  const toneFor = (state: string) =>
    state === 'Normal' ? 'healthy' : state === 'Critical' || state === 'Low' ? 'critical' : 'warning';

  // Build chart data arrays from API responses — strip timestamp for TrendChart compatibility
  const hrData   = (hrHistory?.data   ?? []).map(({ t, value }) => ({ t, value }));
  const spo2Data = (spo2History?.data ?? []).map(({ t, value }) => ({ t, value }));

  const bpData = (() => {
    const sys = (bpSysHistory?.data ?? []).map(({ t, value }) => ({ t, systolic: value }));
    const dia = (bpDiaHistory?.data ?? []).map(({ t, value }) => ({ t, diastolic: value }));
    const map: Record<string, { t: string; systolic?: number; diastolic?: number }> = {};
    sys.forEach(p => { map[p.t] = { t: p.t, systolic: p.systolic }; });
    dia.forEach(p => {
      if (map[p.t]) map[p.t].diastolic = p.diastolic;
      else map[p.t] = { t: p.t, diastolic: p.diastolic };
    });
    return Object.values(map);
  })();

  // ── Last-sync display helper ───────────────────────────────────────────
  const lastSync = (() => {
    if (!device?.lastSeen) return offline ? '42 minutes ago' : '10 seconds ago';
    const diff = Date.now() - new Date(device.lastSeen).getTime();
    const mins = Math.floor(diff / 60_000);
    if (mins < 1)   return 'Just now';
    if (mins < 60)  return `${mins} minutes ago`;
    return `${Math.floor(mins / 60)} hours ago`;
  })();

  return (
    <div>
      <PageHeader
        title="Patient vitals"
        subtitle={`${patientName}${patientId ? ` · ${patientId}` : ''} · streamed continuously through Apache Kafka`}
        backTo={{ to: `/patients/${patientId}`, label: 'Back to Patient 360' }}
        actions={
          <div className="flex items-center gap-2">
            <label htmlFor="vitals-patient" className="text-xs text-slate-500">Patient</label>
            <Select
              id="vitals-patient"
              value={patientId}
              onChange={(e) => navigate(`/vitals/${e.target.value}`)}
              className="h-9 w-48"
            >
              {patientList.map((p) => (
                <option key={p.id} value={p.id}>{p.name} ({p.id})</option>
              ))}
            </Select>
          </div>
        }
      />

      {offline && (
        <div className="mb-4">
          <Notice tone="warning" title="Wearable offline">
            This patient's device has not reported for 42 minutes. Values below are the last known readings and are not live.
          </Notice>
        </div>
      )}

      {/* ── Current vitals tiles ── */}
      {vitalsLoading ? (
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
          {[...Array(5)].map((_, i) => <SkeletonBlock key={i} className="h-24" />)}
        </div>
      ) : (
        <section aria-label="Current vitals" className="grid grid-cols-2 gap-3 lg:grid-cols-5">
          {vitalCards.map((vital) => (
            <div key={vital.label} className="rounded-lg border border-slate-200 bg-white p-4 shadow-card">
              <div className="flex items-center justify-between">
                <p className="text-2xs font-medium uppercase tracking-wide text-slate-500">{vital.label}</p>
                <StatusDot tone={toneFor(vital.state)} pulse={!offline && vital.state !== 'Normal'} />
              </div>
              <p className="mt-2 text-2xl font-semibold tabular text-slate-900">
                {vital.value}
                <span className="ml-1 text-xs font-normal text-slate-500">{vital.unit}</span>
              </p>
              <p className="mt-1">
                <Badge tone={toneFor(vital.state)}>{vital.state}</Badge>
              </p>
            </div>
          ))}
        </section>
      )}

      {/* ── History charts + wearable card ── */}
      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader
            title="Heart rate — last 24 hours"
            description="Sampled every 5 minutes from the wearable stream"
          />
          <CardBody>
            <TrendChart
              data={hrData}
              xKey="t"
              series={[{ key: 'value', name: 'Heart rate (BPM)', color: '#1f6fd0' }]}
              domain={[40, 160]}
              area
              height={220}
            />
          </CardBody>
        </Card>

        <Card>
          <CardHeader
            title="Blood pressure — last 7 days"
            description="Daily average of home and clinic readings"
          />
          <CardBody>
            <TrendChart
              data={bpData}
              xKey="t"
              series={[
                { key: 'systolic',  name: 'Systolic',  color: '#1f6fd0' },
                { key: 'diastolic', name: 'Diastolic', color: '#0e9f7e' },
              ]}
              domain={[60, 160]}
              height={220}
            />
          </CardBody>
        </Card>

        <Card>
          <CardHeader
            title="SpO₂ — last 24 hours"
            description="Peripheral oxygen saturation"
          />
          <CardBody>
            <TrendChart
              data={spo2Data}
              xKey="t"
              series={[{ key: 'value', name: 'SpO₂ (%)', color: '#0e9f7e' }]}
              domain={[88, 100]}
              area
              height={220}
            />
          </CardBody>
        </Card>

        <Card>
          <CardHeader
            title="Wearable device"
            icon={<WatchIcon className="h-4 w-4" />}
            actions={
              <span className="flex items-center gap-1.5 text-2xs font-medium uppercase tracking-wide text-slate-500">
                <StatusDot tone={offline ? 'warning' : 'healthy'} pulse={!offline} />
                {offline ? 'Offline' : 'Live'}
              </span>
            }
          />
          <CardBody className="pt-0">
            <dl>
              <DefinitionRow
                label="Device"
                value={device?.displayName ?? 'Smart Watch · —'}
              />
              <DefinitionRow
                label="Connection"
                value={
                  <Badge tone={offline ? 'warning' : 'healthy'} dot>
                    {offline ? 'Disconnected' : 'Connected'}
                  </Badge>
                }
              />
              <DefinitionRow label="Last sync" value={lastSync} />
              <DefinitionRow
                label="Data stream"
                value={
                  <Badge tone={offline ? 'neutral' : 'healthy'} dot>
                    {offline ? 'Inactive' : 'Active'}
                  </Badge>
                }
              />
              <DefinitionRow
                label="Kafka topic"
                value={
                  <span className="font-mono-clinical text-xs">
                    {device?.kafkaTopic ?? 'vitals.raw'}
                  </span>
                }
              />
            </dl>
            <div className="mt-4 flex gap-2">
              <LinkButton to="/monitoring" size="sm" variant="primary">
                Open live monitoring
              </LinkButton>
              <LinkButton to="/status" size="sm">
                Device diagnostics
              </LinkButton>
            </div>
            <DemoDataNote className="mt-3" />
          </CardBody>
        </Card>
      </div>
    </div>
  );
}
