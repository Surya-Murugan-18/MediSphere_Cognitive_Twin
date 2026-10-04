import React from 'react';
import type { StatusTone } from '../../types/clinical';

const barTones: Record<StatusTone, string> = {
  healthy: 'bg-teal-500',
  warning: 'bg-amber-500',
  critical: 'bg-critical-500',
  info: 'bg-brand-500',
  neutral: 'bg-slate-400'
};

export function ProgressBar({
  label,
  value,
  tone = 'info',
  caption





}: {label?: string;value: number;tone?: StatusTone;caption?: string;}) {
  return (
    <div>
      {(label || caption) &&
      <div className="mb-1.5 flex items-baseline justify-between gap-3">
          {label && <span className="text-xs font-medium text-slate-700">{label}</span>}
          <span className="text-xs font-semibold tabular text-slate-900">{caption ?? `${value}%`}</span>
        </div>
      }
      <div
        className="h-1.5 w-full overflow-hidden rounded-full bg-slate-100"
        role="progressbar"
        aria-valuenow={value}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={label}>
        
        <div className={`h-full rounded-full transition-[width] duration-300 ease-out ${barTones[tone]}`} style={{ width: `${Math.min(100, Math.max(0, value))}%` }} />
      </div>
    </div>);

}

export function adherenceTone(value: number): StatusTone {
  if (value >= 85) return 'healthy';
  if (value >= 70) return 'warning';
  return 'critical';
}