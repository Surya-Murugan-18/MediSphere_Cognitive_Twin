import React from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getAlerts } from '../../api/alerts';
import { Badge } from '../ui/Badge';

/**
 * NotificationPanel — replaces static notifications with real latest alerts.
 *
 * Per tasks.md F5.6: replace hardcoded notifications with real latest alerts.
 * Fetches the 5 most recent Unacknowledged + Acknowledged alerts.
 * Preserves existing NotificationPanel UI.
 */
export function NotificationPanel({ onNavigate }: {onNavigate: () => void;}) {
  const { data: alertsPage } = useQuery({
    queryKey: ['alerts', { size: 5 }],
    queryFn:  () => getAlerts({ size: 5, page: 0 }),
    staleTime: 30_000,
    refetchInterval: 30_000,
  });

  const alerts = alertsPage?.content ?? [];
  const criticalCount = alerts.filter(a => a.severity === 'HIGH' && a.status === 'Unacknowledged').length;

  return (
    <div className="absolute right-0 top-11 z-40 w-80 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-panel">
      <div className="flex items-center justify-between border-b border-slate-200 px-3 py-2.5">
        <p className="text-sm font-semibold text-slate-900">Notifications</p>
        {criticalCount > 0 && <Badge tone="critical">{criticalCount} critical</Badge>}
      </div>
      <div className="max-h-96 overflow-y-auto">
        {alerts.length === 0 ? (
          <div className="px-3 py-4 text-sm text-slate-400 text-center">No recent alerts</div>
        ) : (
          <ul>
            {alerts.map((alert) => (
              <li key={alert.id}>
                <Link
                  to={`/alerts/${alert.id}`}
                  onClick={onNavigate}
                  className="block px-3 py-2.5 transition-colors duration-150 ease-out hover:bg-brand-50"
                >
                  <div className="flex items-start gap-2">
                    <span
                      className={`mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full ${
                        alert.severity === 'HIGH'   ? 'bg-critical-500' :
                        alert.severity === 'MEDIUM' ? 'bg-amber-500' :
                                                      'bg-brand-500'
                      }`}
                      aria-hidden="true"
                    />
                    <div className="min-w-0">
                      <p className="text-sm text-slate-800">
                        {alert.patientName} — {alert.event}
                      </p>
                      <p className="text-xs text-slate-500">
                        {alert.currentValue ?? ''} · {alert.status}
                      </p>
                      <p className="mt-0.5 text-2xs text-slate-400">
                        {alert.detectedAt
                          ? new Date(alert.detectedAt).toLocaleString('en-GB', { hour12: false, hour: '2-digit', minute: '2-digit' })
                          : ''}
                      </p>
                    </div>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="border-t border-slate-200 px-3 py-2">
        <Link
          to="/alerts"
          onClick={onNavigate}
          className="text-xs font-medium text-brand-600 hover:text-brand-700"
        >
          View all clinical alerts
        </Link>
      </div>
    </div>
  );
}
