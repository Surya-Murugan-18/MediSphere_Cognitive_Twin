import { useState } from 'react';
import { ArrowDownRightIcon, ArrowRightIcon, ArrowUpRightIcon, XIcon } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '../components/ui/PageHeader';
import { Card, CardBody, CardHeader, DefinitionRow } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button, LinkButton } from '../components/ui/Button';
import { FilterSelect, Select } from '../components/ui/Field';
import { TableShell, Td, Th, Tr } from '../components/ui/Table';
import { EmptyState, SkeletonRows } from '../components/ui/States';
import { getLabResults } from '../api/labs';
import { getPatients } from '../api/patients';
import type { LabResult } from '../schemas/lab.schema';

// ── Status tone helper ────────────────────────────────────────────────────

const statusTone = (status: LabResult['status']) =>
  status === 'Normal' ? 'healthy' : status === 'Pending' ? 'neutral' : 'warning';

// ── Trend icon component — unchanged from original design ─────────────────

function TrendIcon({ trend }: { trend: LabResult['trend'] }) {
  if (trend === 'up')   return <ArrowUpRightIcon   className="h-4 w-4 text-critical-500" aria-label="Trending up" />;
  if (trend === 'down') return <ArrowDownRightIcon className="h-4 w-4 text-teal-600"     aria-label="Trending down" />;
  return                       <ArrowRightIcon     className="h-4 w-4 text-slate-400"    aria-label="Stable" />;
}

export function LabResults() {
  // ── Patient selector state ────────────────────────────────────────────
  const [patientId, setPatientId] = useState('');

  // ── Filter state ──────────────────────────────────────────────────────
  const [dateRange, setDateRange] = useState('Last 90 days');
  const [testType,  setTestType]  = useState('All types');
  const [status,    setStatus]    = useState('All statuses');

  // ── Detail panel selection ────────────────────────────────────────────
  const [selected, setSelected] = useState<LabResult | null>(null);

  // ── Patient list for selector dropdown ────────────────────────────────
  const { data: patientsPage } = useQuery({
    queryKey: ['patients-labs-selector'],
    queryFn:  () => getPatients({}, 0, 100),
    staleTime: 2 * 60 * 1000,
    // Auto-select first patient once loaded
    select: (data) => {
      if (!patientId && data.content.length > 0) {
        // Will trigger setPatientId on first render with data
      }
      return data;
    },
  });

  const patientList = patientsPage?.content ?? [];

  // Set first patient as default once available
  const activePatientId = patientId || patientList[0]?.id || '';
  const activePatient   = patientList.find(p => p.id === activePatientId);

  // ── Date range → API params ───────────────────────────────────────────
  const dateFrom = (() => {
    const now = new Date();
    switch (dateRange) {
      case 'Last 30 days': return new Date(now.getTime() - 30 * 86400_000).toISOString().slice(0, 10);
      case 'Last 7 days':  return new Date(now.getTime() -  7 * 86400_000).toISOString().slice(0, 10);
      default:             return new Date(now.getTime() - 90 * 86400_000).toISOString().slice(0, 10);
    }
  })();
  const dateTo = new Date().toISOString().slice(0, 10);

  // ── Lab results query ─────────────────────────────────────────────────
  const { data: labPage, isLoading, isError } = useQuery({
    queryKey: ['labs', activePatientId, testType, status, dateFrom, dateTo],
    queryFn:  () => getLabResults(activePatientId, {
      category: testType !== 'All types'     ? testType : undefined,
      status:   status   !== 'All statuses'  ? status   : undefined,
      dateFrom,
      dateTo,
      size: 100,
    }),
    enabled:  Boolean(activePatientId),
  });

  const rows = labPage?.content ?? [];

  const clearFilters = () => {
    setTestType('All types');
    setStatus('All statuses');
    setSelected(null);
  };

  return (
    <div>
      <PageHeader
        title="Laboratory results"
        subtitle={`${activePatient?.name ?? activePatientId} · ${activePatientId} · received as FHIR Observation resources`}
        actions={
          <div className="flex items-center gap-2">
            <label htmlFor="lab-patient" className="text-xs text-slate-500">Patient</label>
            <Select
              id="lab-patient"
              value={activePatientId}
              onChange={(e) => {
                setPatientId(e.target.value);
                setSelected(null);
              }}
              className="h-9 w-48"
            >
              {patientList.map((p) => (
                <option key={p.id} value={p.id}>{p.name} ({p.id})</option>
              ))}
            </Select>
          </div>
        }
      />

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        {/* ── Results table ── */}
        <Card className={selected ? 'xl:col-span-2' : 'xl:col-span-3'}>
          <CardHeader
            title="Results"
            description="Select a test to open the detailed result panel"
          />
          <div className="flex flex-wrap gap-3 border-b border-slate-200 p-4">
            <FilterSelect label="Date"      value={dateRange} onChange={setDateRange} options={['Last 90 days', 'Last 30 days', 'Last 7 days']} />
            <FilterSelect label="Test type" value={testType}  onChange={v => { setTestType(v); setSelected(null); }} options={['All types', 'Metabolic', 'Lipids', 'Cardiac', 'Hematology']} />
            <FilterSelect label="Status"    value={status}    onChange={v => { setStatus(v);   setSelected(null); }} options={['All statuses', 'High', 'Low', 'Normal', 'Pending']} />
          </div>

          {isLoading ? (
            <SkeletonRows rows={5} cols={6} />
          ) : isError ? (
            <EmptyState
              title="Could not load lab results"
              description="There was a problem fetching laboratory data. Please try again."
            />
          ) : rows.length === 0 ? (
            <EmptyState
              title="No lab results"
              description="No laboratory observations match these filters for this patient. Results appear here as soon as the laboratory system publishes them over FHIR."
              action={<Button size="sm" onClick={clearFilters}>Clear filters</Button>}
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
                  <Th align="center">Trend</Th>
                </tr>
              </thead>
              <tbody>
                {rows.map((lab) => (
                  <Tr
                    key={lab.id}
                    onClick={() => setSelected(lab)}
                    className={selected?.id === lab.id ? 'bg-brand-50/70' : ''}
                  >
                    <Td className="font-medium text-slate-900">{lab.test}</Td>
                    <Td className="tabular">{lab.result}</Td>
                    <Td className="text-slate-500">{lab.referenceRange ?? '—'}</Td>
                    <Td>
                      <Badge tone={statusTone(lab.status)}>{lab.status}</Badge>
                    </Td>
                    <Td className="text-slate-500">{lab.date}</Td>
                    <Td align="center">
                      <span className="inline-flex justify-center">
                        <TrendIcon trend={lab.trend} />
                      </span>
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </TableShell>
          )}
        </Card>

        {/* ── Detail panel — derived from selected row, no separate API call ── */}
        {selected && (
          <Card>
            <CardHeader
              title={selected.test}
              description={`Result detail · ${selected.date}`}
              actions={
                <button
                  type="button"
                  onClick={() => setSelected(null)}
                  aria-label="Close result detail"
                  className="rounded p-1 text-slate-400 transition-colors duration-150 ease-out hover:bg-slate-100 hover:text-slate-600"
                >
                  <XIcon className="h-4 w-4" aria-hidden="true" />
                </button>
              }
            />
            <CardBody className="pt-0">
              <dl>
                <DefinitionRow
                  label="Current result"
                  value={
                    <span className="flex items-center justify-end gap-2">
                      <span className="tabular text-base font-semibold">{selected.result}</span>
                      <Badge tone={statusTone(selected.status)}>{selected.status}</Badge>
                    </span>
                  }
                />
                <DefinitionRow label="Previous result"  value={selected.previous || '—'} />
                <DefinitionRow
                  label="Trend"
                  value={
                    <span className="flex items-center justify-end gap-1.5">
                      <TrendIcon trend={selected.trend} />
                      {selected.trend === 'up' ? 'Increasing' : selected.trend === 'down' ? 'Decreasing' : 'Stable'}
                    </span>
                  }
                />
                <DefinitionRow label="Reference range" value={selected.referenceRange ?? '—'} />
              </dl>
              {selected.significance && (
                <div className="mt-4 rounded-md border border-slate-200 bg-slate-50 p-3">
                  <p className="text-2xs font-semibold uppercase tracking-wide text-slate-500">Clinical significance</p>
                  <p className="mt-1 text-sm leading-relaxed text-slate-700">{selected.significance}</p>
                </div>
              )}
              <div className="mt-4 flex gap-2">
                <LinkButton to="/predictions" size="sm" variant="primary">
                  View related prediction
                </LinkButton>
                <LinkButton to={`/twins/${selected.patientId}`} size="sm">
                  View twin
                </LinkButton>
              </div>
            </CardBody>
          </Card>
        )}
      </div>
    </div>
  );
}
