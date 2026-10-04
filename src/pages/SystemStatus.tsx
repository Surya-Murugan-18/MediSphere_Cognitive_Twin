import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '../components/ui/PageHeader';
import { Card, CardBody, CardHeader } from '../components/ui/Card';
import { Badge, StatusDot } from '../components/ui/Badge';
import { Notice, SkeletonBlock } from '../components/ui/States';
import { TableShell, Td, Th, Tr } from '../components/ui/Table';
import { getSystemServices, getSystemEvents } from '../api/system';

export function SystemStatus() {
  // Auto-refresh every 30 seconds per tasks.md F7.6
  const { data: services, isLoading: servicesLoading } = useQuery({
    queryKey: ['system-services'],
    queryFn: getSystemServices,
    staleTime: 30_000,
    refetchInterval: 30_000,
  });

  const { data: events, isLoading: eventsLoading } = useQuery({
    queryKey: ['system-events'],
    queryFn: getSystemEvents,
    staleTime: 30_000,
    refetchInterval: 30_000,
  });

  const degraded = (services ?? []).filter((s) => s.tone !== 'healthy');

  return (
    <div>
      <PageHeader
        title="MediSphere system status"
        subtitle="Health of the integration, streaming, storage and model services backing the platform"
        meta={
          <>
            <Badge tone={degraded.length === 0 ? 'healthy' : 'warning'} dot>
              {servicesLoading
                ? 'Checking…'
                : degraded.length === 0
                ? 'All systems operational'
                : `${degraded.length} service${degraded.length > 1 ? 's' : ''} degraded`}
            </Badge>
            <Badge tone="neutral">Auto-refresh every 30 s</Badge>
          </>
        }
      />

      {degraded.length > 0 && (
        <div className="mb-4">
          <Notice tone="warning" title="One or more services degraded">
            {degraded.map((s) => s.name).join(', ')} — check the service cards below for details.
          </Notice>
        </div>
      )}

      {servicesLoading ? (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 7 }).map((_, i) => <SkeletonBlock key={i} className="h-24" />)}
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
          {(services ?? []).map((service) => (
            <Card key={service.name}>
              <CardBody>
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-slate-900">{service.name}</p>
                    <p className="mt-0.5 text-xs text-slate-500">{service.detail}</p>
                  </div>
                  <StatusDot tone={service.tone as any} pulse={service.tone === 'healthy'} />
                </div>
                <div className="mt-3 flex items-center justify-between">
                  <Badge tone={service.tone as any} dot>{service.state}</Badge>
                  <span className="text-2xs text-slate-500">Uptime {service.uptime}</span>
                </div>
              </CardBody>
            </Card>
          ))}
        </div>
      )}

      <Card className="mt-4">
        <CardHeader
          title="Recent system events"
          description="Platform-level events across integrations and models"
        />
        {eventsLoading ? (
          <SkeletonBlock className="h-32 m-4" />
        ) : (
          <TableShell>
            <thead>
              <tr>
                <Th>Time</Th>
                <Th>Event</Th>
                <Th align="right">Severity</Th>
              </tr>
            </thead>
            <tbody>
              {(events ?? []).length === 0 ? (
                <tr>
                  <td colSpan={3} className="px-4 py-6 text-center text-sm text-slate-400">
                    No system events recorded yet.
                  </td>
                </tr>
              ) : (
                (events ?? []).map((event) => (
                  <Tr key={event.id}>
                    <Td className="font-mono-clinical text-xs text-slate-500">
                      {event.timestamp
                        ? new Date(event.timestamp).toLocaleTimeString('en-GB', { hour12: false })
                        : '—'}
                    </Td>
                    <Td className="text-slate-700">{event.text}</Td>
                    <Td align="right">
                      <Badge tone={event.tone as any}>
                        {event.tone === 'critical' ? 'Critical'
                          : event.tone === 'warning' ? 'Warning'
                          : event.tone === 'healthy' ? 'Resolved' : 'Info'}
                      </Badge>
                    </Td>
                  </Tr>
                ))
              )}
            </tbody>
          </TableShell>
        )}
      </Card>
    </div>
  );
}
