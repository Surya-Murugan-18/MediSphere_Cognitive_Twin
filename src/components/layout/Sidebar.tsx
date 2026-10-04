import React from 'react';
import { NavLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ActivityIcon,
  BellRingIcon,
  BrainIcon,
  ClipboardListIcon,
  FileTextIcon,
  FlaskConicalIcon,
  LayoutDashboardIcon,
  NetworkIcon,
  PersonStandingIcon,
  PieChartIcon,
  RadioIcon,
  ScrollTextIcon,
  ServerCogIcon,
  SettingsIcon,
  ShieldCheckIcon,
  UsersIcon,
  XIcon } from
'lucide-react';
import { primaryNav, secondaryNav, type NavItem } from '../../data/navigation';
import { useAuth } from '../../hooks/useAuth';
import { getAlertCount } from '../../api/alerts';

const icons: Record<string, React.ComponentType<{className?: string;}>> = {
  LayoutDashboard: LayoutDashboardIcon,
  Users: UsersIcon,
  PersonStanding: PersonStandingIcon,
  Activity: ActivityIcon,
  FlaskConical: FlaskConicalIcon,
  Brain: BrainIcon,
  Network: NetworkIcon,
  Radio: RadioIcon,
  BellRing: BellRingIcon,
  ClipboardList: ClipboardListIcon,
  PieChart: PieChartIcon,
  FileText: FileTextIcon,
  ShieldCheck: ShieldCheckIcon,
  ScrollText: ScrollTextIcon,
  ServerCog: ServerCogIcon,
  Settings: SettingsIcon
};

function NavRow({ item, onNavigate, liveAlertBadge }: {item: NavItem; onNavigate?: () => void; liveAlertBadge?: number | null;}) {
  const Icon = icons[item.icon] ?? LayoutDashboardIcon;
  // For the Alerts nav item, show live count when available; fall back to static badge
  const badgeValue = item.to === '/alerts' && liveAlertBadge != null
    ? (liveAlertBadge > 0 ? String(liveAlertBadge) : null)
    : item.badge;
  return (
    <li>
      <NavLink
        to={item.to}
        end={item.to === '/'}
        onClick={onNavigate}
        className={({ isActive }) =>
        `group flex items-center gap-2.5 rounded-md px-2.5 py-2 text-sm transition-colors duration-150 ease-out ${
        isActive ?
        'bg-brand-500/15 text-white before:absolute before:left-0 before:h-6 before:w-0.5 before:rounded-r before:bg-brand-400' :
        'text-navy-50/70 hover:bg-white/5 hover:text-white'} relative`
        }>
        <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
        <span className="truncate">{item.label}</span>
        {badgeValue &&
        <span className="ml-auto rounded bg-critical-500 px-1.5 text-2xs font-semibold text-white">{badgeValue}</span>
        }
      </NavLink>
    </li>);
}

export function Sidebar({ open, onClose }: {open: boolean;onClose: () => void;}) {
  const { currentUser, isAuthenticated } = useAuth();

  // ── Live alert count — per tasks.md F5.6 ─────────────────────────────
  // Fetched independently from navigation.ts badge (which is static metadata).
  // DO NOT modify navigation.ts badge field.
  const { data: alertCountData } = useQuery({
    queryKey: ['alert-count', 'Unacknowledged'],
    queryFn:  () => getAlertCount('Unacknowledged'),
    enabled:  isAuthenticated,
    staleTime: 30_000,
    refetchInterval: 30_000,
  });
  const liveAlertBadge = alertCountData?.count ?? null;

  // Derive display values from authenticated provider.
  // Fall back to safe placeholders during the brief loading window.
  const displayName = currentUser?.name ?? 'Provider';
  const displayRole = currentUser?.role
    ? currentUser.role.charAt(0).toUpperCase() + currentUser.role.slice(1).toLowerCase()
    : 'Clinician';
  const displaySpecialty = currentUser?.specialty ?? '';
  const initials = displayName
    .split(' ')
    .filter((w) => w.length > 0)
    .map((w) => w[0].toUpperCase())
    .slice(0, 2)
    .join('');

  return (
    <>
      {open && <div className="fixed inset-0 z-30 bg-navy-950/40 lg:hidden" onClick={onClose} aria-hidden="true" />}
      <aside
        className={`fixed inset-y-0 left-0 z-40 flex w-60 flex-col bg-navy-900 transition-transform duration-200 ease-out lg:translate-x-0 ${
        open ? 'translate-x-0' : '-translate-x-full'}`
        }
        aria-label="Primary navigation">
        
        <div className="flex items-center justify-between gap-2 border-b border-white/10 px-4 py-4">
          <div className="flex items-center gap-2.5">
            <span className="flex h-8 w-8 items-center justify-center rounded-md bg-brand-500 text-sm font-bold text-white">M</span>
            <div>
              <p className="text-sm font-semibold leading-tight text-white">MediSphere</p>
              <p className="text-2xs leading-tight text-navy-50/60">Cognitive Twin</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded p-1 text-navy-50/70 transition-colors duration-150 ease-out hover:bg-white/10 hover:text-white lg:hidden"
            aria-label="Close navigation">
            
            <XIcon className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>

        <nav className="flex-1 overflow-y-auto px-2.5 py-3">
          <p className="px-2.5 pb-1.5 text-2xs font-semibold uppercase tracking-wider text-navy-50/40">Clinical</p>
          <ul className="space-y-0.5">
            {primaryNav.map((item) =>
            <NavRow key={item.to} item={item} onNavigate={onClose} liveAlertBadge={liveAlertBadge} />
            )}
          </ul>
        </nav>

        <div className="border-t border-white/10 px-2.5 py-3">
          <ul className="space-y-0.5">
            {secondaryNav.map((item) =>
            <NavRow key={item.to} item={item} onNavigate={onClose} />
            )}
          </ul>
          <div className="mt-2 flex items-center gap-2.5 rounded-md bg-white/5 px-2.5 py-2">
            <span className="flex h-7 w-7 items-center justify-center rounded-full bg-brand-500 text-2xs font-semibold text-white">{initials}</span>
            <div className="min-w-0">
              <p className="truncate text-xs font-medium text-white">{displayName}</p>
              <p className="text-2xs text-navy-50/60">
                {displayRole}{displaySpecialty ? ` · ${displaySpecialty}` : ''}
              </p>
            </div>
          </div>
        </div>
      </aside>
    </>);

}