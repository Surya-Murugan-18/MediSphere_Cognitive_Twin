import React from 'react';
import { Link } from 'react-router-dom';
import type { StatusTone } from '../../types/clinical';

interface KpiCardProps {
  label: string;
  value: string;
  caption?: string;
  tone?: StatusTone;
  icon?: React.ReactNode;
  to?: string;
  emphasis?: boolean;
}

const accents: Record<StatusTone, string> = {
  healthy: 'text-teal-600',
  warning: 'text-amber-600',
  critical: 'text-critical-600',
  info: 'text-brand-600',
  neutral: 'text-slate-500'
};

export function KpiCard({ label, value, caption, tone = 'neutral', icon, to, emphasis = false }: KpiCardProps) {
  const content =
  <div
    className={`flex h-full flex-col justify-between rounded-lg border bg-white p-4 shadow-card transition-colors duration-150 ease-out ${
    emphasis ? 'border-critical-200' : 'border-slate-200'} ${
    to ? 'hover:border-brand-300' : ''}`}>
    
      <div className="flex items-start justify-between gap-2">
        <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
        {icon && <span className={accents[tone]}>{icon}</span>}
      </div>
      <p className={`mt-3 text-3xl font-semibold tabular ${emphasis ? 'text-critical-600' : 'text-slate-900'}`}>{value}</p>
      {caption && <p className={`mt-1 text-xs ${accents[tone]}`}>{caption}</p>}
    </div>;


  if (to) {
    return (
      <Link to={to} className="block h-full">
        {content}
      </Link>);

  }
  return content;
}