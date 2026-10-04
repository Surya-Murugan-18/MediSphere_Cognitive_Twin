import React from 'react';
import { AlertTriangleIcon, InboxIcon, LockIcon, PlugZapIcon, CheckCircle2Icon } from 'lucide-react';

export function EmptyState({
  title,
  description,
  action,
  icon





}: {title: string;description: string;action?: React.ReactNode;icon?: React.ReactNode;}) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
      <span className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-slate-100 text-slate-400">
        {icon ?? <InboxIcon className="h-5 w-5" aria-hidden="true" />}
      </span>
      <p className="text-sm font-semibold text-slate-800">{title}</p>
      <p className="mt-1 max-w-sm text-xs text-slate-500">{description}</p>
      {action && <div className="mt-4">{action}</div>}
    </div>);

}

export function ErrorState({
  title,
  description,
  action




}: {title: string;description: string;action?: React.ReactNode;}) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
      <span className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-critical-50 text-critical-600">
        <PlugZapIcon className="h-5 w-5" aria-hidden="true" />
      </span>
      <p className="text-sm font-semibold text-slate-800">{title}</p>
      <p className="mt-1 max-w-sm text-xs text-slate-500">{description}</p>
      {action && <div className="mt-4">{action}</div>}
    </div>);

}

export function PermissionDenied({ detail }: {detail: string;}) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
      <span className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
        <LockIcon className="h-5 w-5" aria-hidden="true" />
      </span>
      <p className="text-sm font-semibold text-slate-800">Permission denied</p>
      <p className="mt-1 max-w-sm text-xs text-slate-500">{detail}</p>
    </div>);

}

type NoticeTone = 'warning' | 'critical' | 'healthy' | 'info';

const noticeStyles: Record<NoticeTone, string> = {
  warning: 'border-amber-200 bg-amber-50 text-amber-800',
  critical: 'border-critical-200 bg-critical-50 text-critical-700',
  healthy: 'border-teal-200 bg-teal-50 text-teal-700',
  info: 'border-brand-200 bg-brand-50 text-brand-700'
};

export function Notice({
  tone = 'info',
  title,
  children,
  action





}: {tone?: NoticeTone;title: string;children?: React.ReactNode;action?: React.ReactNode;}) {
  const Icon = tone === 'healthy' ? CheckCircle2Icon : AlertTriangleIcon;
  return (
    <div className={`flex items-start justify-between gap-4 rounded-lg border px-4 py-3 ${noticeStyles[tone]}`} role="status">
      <div className="flex gap-2.5">
        <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        <div>
          <p className="text-sm font-semibold">{title}</p>
          {children && <div className="mt-0.5 text-xs leading-relaxed opacity-90">{children}</div>}
        </div>
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>);

}

export function SkeletonRows({ rows = 5, cols = 5 }: {rows?: number;cols?: number;}) {
  return (
    <div className="divide-y divide-slate-100" aria-hidden="true">
      {Array.from({ length: rows }).map((_, r) =>
      <div key={r} className="flex items-center gap-4 px-3 py-3">
          {Array.from({ length: cols }).map((_, c) =>
        <div
          key={c}
          className="h-3 animate-pulse rounded bg-slate-100"
          style={{ width: c === 0 ? '18%' : `${10 + (r + c) % 3 * 6}%` }} />

        )}
        </div>
      )}
    </div>);

}

export function SkeletonBlock({ className = '' }: {className?: string;}) {
  return <div className={`animate-pulse rounded-lg bg-slate-100 ${className}`} aria-hidden="true" />;
}

export function LoadingLabel({ label }: {label: string;}) {
  return (
    <p className="flex items-center gap-2 px-4 py-3 text-xs text-slate-500" role="status">
      <span className="h-3 w-3 animate-spin rounded-full border-2 border-slate-300 border-t-brand-500" aria-hidden="true" />
      {label}
    </p>);

}