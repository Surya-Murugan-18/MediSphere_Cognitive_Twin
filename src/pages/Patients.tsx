import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlusIcon, SearchIcon } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '../components/ui/PageHeader';
import { Card, CardHeader } from '../components/ui/Card';
import { Badge, RiskBadge } from '../components/ui/Badge';
import { LinkButton } from '../components/ui/Button';
import { FilterSelect } from '../components/ui/Field';
import { TableShell, Td, Th, Tr } from '../components/ui/Table';
import { EmptyState, SkeletonRows } from '../components/ui/States';
import { getPatients, getFilterOptions } from '../api/patients';
import type { TwinStatus } from '../types/clinical';

const twinTone: Record<TwinStatus, 'healthy' | 'warning' | 'neutral'> = {
  Synchronized: 'healthy',
  Syncing:      'warning',
  Stale:        'warning',
  'Not Created': 'neutral',
};

const PAGE_SIZE = 20;

export function Patients() {
  const navigate = useNavigate();

  // ── Filter state ─────────────────────────────────────────────────────────
  const [query,    setQuery]    = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [risk,     setRisk]     = useState('All risk');
  const [condition, setCondition] = useState('All conditions');
  const [status,   setStatus]   = useState('All statuses');
  const [provider, setProvider] = useState('All providers');
  const [page,     setPage]     = useState(0);

  // Debounce search input 350 ms
  useEffect(() => {
    const t = window.setTimeout(() => setDebouncedQuery(query), 350);
    return () => window.clearTimeout(t);
  }, [query]);

  // Reset to page 0 whenever a filter changes
  useEffect(() => { setPage(0); }, [debouncedQuery, risk, condition, status, provider]);

  // ── Filter options (conditions + providers dropdowns) ─────────────────────
  const { data: filterOpts } = useQuery({
    queryKey: ['patient-filter-options'],
    queryFn:  getFilterOptions,
    staleTime: 5 * 60 * 1000, // 5 min — filter options change rarely
  });

  const conditions = ['All conditions', ...(filterOpts?.conditions ?? [])];
  const providers  = ['All providers',  ...(filterOpts?.providers  ?? [])];

  // ── Patient list query ────────────────────────────────────────────────────
  const { data, isLoading, isError } = useQuery({
    queryKey: ['patients', debouncedQuery, risk, condition, status, provider, page],
    queryFn:  () => getPatients(
      { search: debouncedQuery, riskLevel: risk, condition, status, providerName: provider },
      page,
      PAGE_SIZE,
    ),
    placeholderData: (prev) => prev, // keep previous data while fetching next page
  });

  const patients      = data?.content ?? [];
  const totalElements = data?.totalElements ?? 0;
  const totalPages    = data?.totalPages ?? 0;

  // ── Helpers ───────────────────────────────────────────────────────────────
  const twinStatusOf = (s: string): TwinStatus =>
    (['Synchronized', 'Syncing', 'Stale', 'Not Created'].includes(s)
      ? s as TwinStatus
      : 'Not Created');

  return (
    <div>
      <PageHeader
        title="Patients"
        subtitle={`${totalElements.toLocaleString()} onboarded patients in this care group`}
        actions={
          <LinkButton to="/patients/new" variant="primary">
            <PlusIcon className="h-4 w-4" aria-hidden="true" />
            Add patient
          </LinkButton>
        }
      />

      <Card>
        <CardHeader
          title="Patient registry"
          description="Only the minimum identifying information required for care coordination is shown in this list."
        />

        {/* ── Filters ── */}
        <div className="border-b border-slate-200 p-4">
          <div className="relative mb-3 max-w-md">
            <SearchIcon className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search by patient name, patient ID, FHIR ID"
              aria-label="Search patients"
              className="h-9 w-full rounded-md border border-slate-300 bg-white pl-8 pr-3 text-sm placeholder:text-slate-400 focus:border-brand-500 focus:outline-none"
            />
          </div>
          <div className="flex flex-wrap gap-3">
            <FilterSelect label="Risk"         value={risk}      onChange={setRisk}      options={['All risk', 'High', 'Medium', 'Low']} />
            <FilterSelect label="Condition"    value={condition} onChange={setCondition} options={conditions} />
            <FilterSelect label="Status"       value={status}    onChange={setStatus}    options={['All statuses', 'Active', 'Inactive', 'Pending Consent']} />
            <FilterSelect label="Provider"     value={provider}  onChange={setProvider}  options={providers} />
          </div>
        </div>

        {/* ── Table ── */}
        {isError ? (
          <EmptyState
            title="Could not load patients"
            description="There was a problem connecting to the server. Please try again."
          />
        ) : isLoading ? (
          <SkeletonRows rows={8} cols={7} />
        ) : patients.length === 0 ? (
          <EmptyState
            title="No patients match these filters"
            description="Adjust the search term or clear one of the active filters to widen the patient registry results."
          />
        ) : (
          <TableShell>
            <thead>
              <tr>
                <Th>Patient ID</Th>
                <Th>Patient name</Th>
                <Th align="right">Age</Th>
                <Th>Conditions</Th>
                <Th>Risk</Th>
                <Th>Twin status</Th>
                <Th>Last updated</Th>
                <Th align="right">Actions</Th>
              </tr>
            </thead>
            <tbody>
              {patients.map((patient) => (
                <Tr key={patient.id}>
                  <Td className="font-mono-clinical text-xs text-slate-500">{patient.id}</Td>
                  <Td>
                    <button
                      type="button"
                      onClick={() => navigate(`/patients/${patient.id}`)}
                      className="font-medium text-slate-900 hover:text-brand-600"
                    >
                      {patient.name}
                    </button>
                    {patient.status === 'Pending Consent' && (
                      <Badge tone="warning" className="ml-2">Consent missing</Badge>
                    )}
                  </Td>
                  <Td align="right" className="tabular">
                    {/* Age computed client-side from dob if API returns 0 */}
                    {computeAge(patient.dob)}
                  </Td>
                  <Td className="text-slate-600">{patient.conditions.join(', ') || '—'}</Td>
                  <Td>
                    <RiskBadge risk={patient.riskLevel as 'High' | 'Medium' | 'Low'} />
                  </Td>
                  <Td>
                    <Badge tone={twinTone[twinStatusOf(patient.twinStatus)]} dot>
                      {patient.twinStatus}
                    </Badge>
                  </Td>
                  <Td className="whitespace-nowrap text-slate-500">
                    {formatRelative(patient.updatedAt)}
                  </Td>
                  <Td align="right">
                    <div className="flex justify-end gap-1.5 whitespace-nowrap text-xs font-medium">
                      <button type="button" onClick={() => navigate(`/patients/${patient.id}`)}   className="rounded px-1.5 py-1 text-brand-600 hover:bg-brand-50">View</button>
                      <button type="button" onClick={() => navigate(`/twins/${patient.id}`)}      className="rounded px-1.5 py-1 text-brand-600 hover:bg-brand-50">View twin</button>
                      <button type="button" onClick={() => navigate(`/predictions?patientId=${patient.id}`)} className="rounded px-1.5 py-1 text-brand-600 hover:bg-brand-50">Predictions</button>
                      <button type="button" onClick={() => navigate(`/alerts?patientId=${patient.id}`)}      className="rounded px-1.5 py-1 text-brand-600 hover:bg-brand-50">Alerts</button>
                    </div>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </TableShell>
        )}

        {/* ── Pagination ── */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3">
            <p className="text-xs text-slate-500">
              Page {page + 1} of {totalPages} · {totalElements.toLocaleString()} patients
            </p>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="rounded px-3 py-1.5 text-xs font-medium text-brand-600 hover:bg-brand-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                Previous
              </button>
              <button
                type="button"
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="rounded px-3 py-1.5 text-xs font-medium text-brand-600 hover:bg-brand-50 disabled:cursor-not-allowed disabled:opacity-40"
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

// ── Helpers ───────────────────────────────────────────────────────────────

function computeAge(dob?: string | null): number | string {
  if (!dob) return '—';
  try {
    const birth = new Date(dob);
    const today = new Date();
    let age = today.getFullYear() - birth.getFullYear();
    const m = today.getMonth() - birth.getMonth();
    if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) age--;
    return age;
  } catch {
    return '—';
  }
}

function formatRelative(ts?: string | null): string {
  if (!ts) return '—';
  try {
    const diff = Date.now() - new Date(ts).getTime();
    const mins  = Math.floor(diff / 60_000);
    if (mins  <  1)  return 'Just now';
    if (mins  < 60)  return `${mins} min ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs   < 24)  return `${hrs} h ago`;
    const days = Math.floor(hrs / 24);
    return `${days}d ago`;
  } catch {
    return '—';
  }
}
