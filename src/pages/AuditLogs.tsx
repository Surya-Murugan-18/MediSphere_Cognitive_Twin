import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '../components/ui/PageHeader';
import { Card, CardHeader } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { FilterSelect } from '../components/ui/Field';
import { TableShell, Td, Th, Tr } from '../components/ui/Table';
import { EmptyState, SkeletonRows } from '../components/ui/States';
import { getAuditLogs, downloadAuditExport } from '../api/audit';

// ── Date filter → ISO timestamp conversion ────────────────────────────────

function dateFilterToRange(label: string): { from?: string; to?: string } {
  const now = new Date();
  const startOf = (d: Date) => {
    const s = new Date(d);
    s.setHours(0, 0, 0, 0);
    return s.toISOString();
  };
  if (label === 'Today') {
    return { from: startOf(now), to: new Date(now.setHours(23, 59, 59, 999)).toISOString() };
  }
  if (label === 'Yesterday') {
    const y = new Date(); y.setDate(y.getDate() - 1);
    return { from: startOf(y), to: new Date(y.setHours(23, 59, 59, 999)).toISOString() };
  }
  if (label === 'Last 7 days') {
    const from = new Date(); from.setDate(from.getDate() - 7);
    return { from: startOf(from) };
  }
  return {};
}

export function AuditLogs() {
  const [user,    setUser]    = useState('');
  const [patient, setPatient] = useState('');
  const [action,  setAction]  = useState('');
  const [module,  setModule]  = useState('');
  const [date,    setDate]    = useState('Today');
  const [page,    setPage]    = useState(0);

  const dateRange = dateFilterToRange(date);

  const { data, isLoading } = useQuery({
    queryKey: ['audit', { user, patient, action, module, date, page }],
    queryFn: () => getAuditLogs({
      user:    user    || undefined,
      patient: patient || undefined,
      action:  action  || undefined,
      module:  module  || undefined,
      ...dateRange,
      page,
      size: 20,
    }),
    staleTime: 30_000,
  });

  const rows     = data?.content ?? [];
  const total    = data?.totalPages ?? 0;

  const clear = () => {
    setUser(''); setPatient(''); setAction('');
    setModule(''); setDate('Today'); setPage(0);
  };

  return (
    <div>
      <PageHeader
        title="Clinical audit trail"
        subtitle="Every access, review and approval across the platform is recorded immutably"
        meta={
          <>
            <Badge tone="healthy" dot>Audit logging active</Badge>
            <Badge tone="neutral">Retention: 7 years</Badge>
          </>
        }
      />

      <Card>
        <CardHeader
          title="Audit events"
          description="All times UTC · server-side filtered and paginated"
        />
        <div className="flex flex-wrap items-end gap-3 border-b border-slate-200 p-4">
          <FilterSelect label="User"    value={user    || 'All users'}    onChange={v => { setUser(v === 'All users' ? '' : v);       setPage(0); }} options={['All users']} />
          <FilterSelect label="Patient" value={patient || 'All patients'} onChange={v => { setPatient(v === 'All patients' ? '' : v); setPage(0); }} options={['All patients']} />
          <FilterSelect label="Action"  value={action  || 'All actions'}  onChange={v => { setAction(v === 'All actions' ? '' : v);   setPage(0); }} options={['All actions']} />
          <FilterSelect label="Module"  value={module  || 'All modules'}  onChange={v => { setModule(v === 'All modules' ? '' : v);   setPage(0); }} options={['All modules']} />
          <FilterSelect label="Date"    value={date}                       onChange={v => { setDate(v); setPage(0); }}                              options={['Today', 'Yesterday', 'Last 7 days']} />
          <Button size="sm" onClick={clear}>Clear</Button>
          <Button size="sm" onClick={() => downloadAuditExport({ user: user || undefined, patient: patient || undefined, action: action || undefined, module: module || undefined, ...dateRange })}>
            Export CSV
          </Button>
        </div>

        {isLoading ? (
          <SkeletonRows rows={7} cols={6} />
        ) : rows.length === 0 ? (
          <EmptyState
            title="No audit events match these filters"
            description="Try widening the filter selection to see more recorded activity."
            action={<Button size="sm" onClick={clear}>Clear filters</Button>}
          />
        ) : (
          <TableShell>
            <thead>
              <tr>
                <Th>Timestamp</Th>
                <Th>User</Th>
                <Th>Action</Th>
                <Th>Patient</Th>
                <Th>Module</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {rows.map((entry) => (
                <Tr key={entry.id}>
                  <Td className="font-mono-clinical text-xs text-slate-500">
                    {entry.timestamp ? new Date(entry.timestamp).toLocaleString('en-GB') : '—'}
                  </Td>
                  <Td className="whitespace-nowrap font-medium text-slate-900">
                    {entry.userName ?? entry.userId ?? '—'}
                  </Td>
                  <Td className="text-slate-700">{entry.action ?? '—'}</Td>
                  <Td className="text-slate-600">{entry.patientName ?? entry.patientId ?? '—'}</Td>
                  <Td className="text-slate-600">{entry.module ?? '—'}</Td>
                  <Td>
                    <Badge tone={entry.status === 'Success' ? 'healthy' : 'critical'}>
                      {entry.status ?? '—'}
                    </Badge>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </TableShell>
        )}

        {/* Pagination */}
        {total > 1 && (
          <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3">
            <span className="text-xs text-slate-500">Page {page + 1} of {total}</span>
            <div className="flex gap-2">
              <Button size="sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>← Prev</Button>
              <Button size="sm" disabled={page >= total - 1} onClick={() => setPage(p => p + 1)}>Next →</Button>
            </div>
          </div>
        )}
      </Card>
    </div>
  );
}
