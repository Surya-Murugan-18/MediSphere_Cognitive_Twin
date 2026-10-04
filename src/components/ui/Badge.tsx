import React from 'react';
import type { RiskLevel, StatusTone } from '../../types/clinical';

const tones: Record<StatusTone, string> = {
  healthy: 'bg-teal-50 text-teal-700 border-teal-100',
  warning: 'bg-amber-50 text-amber-700 border-amber-100',
  critical: 'bg-critical-50 text-critical-700 border-critical-100',
  info: 'bg-brand-50 text-brand-700 border-brand-100',
  neutral: 'bg-slate-100 text-slate-600 border-slate-200'
};

const dotTones: Record<StatusTone, string> = {
  healthy: 'bg-teal-500',
  warning: 'bg-amber-500',
  critical: 'bg-critical-500',
  info: 'bg-brand-500',
  neutral: 'bg-slate-400'
};

interface BadgeProps {
  tone?: StatusTone;
  dot?: boolean;
  children: React.ReactNode;
  className?: string;
}

export function Badge({ tone = 'neutral', dot = false, children, className = '' }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded border px-2 py-0.5 text-2xs font-semibold uppercase tracking-wide ${tones[tone]} ${className}`}>
      
      {dot && <span className={`h-1.5 w-1.5 rounded-full ${dotTones[tone]}`} aria-hidden="true" />}
      {children}
    </span>);

}

export function StatusDot({ tone = 'neutral', pulse = false }: {tone?: StatusTone;pulse?: boolean;}) {
  return (
    <span className="relative inline-flex h-2 w-2" aria-hidden="true">
      {pulse && <span className={`absolute inline-flex h-full w-full animate-ping rounded-full opacity-60 ${dotTones[tone]}`} />}
      <span className={`relative inline-flex h-2 w-2 rounded-full ${dotTones[tone]}`} />
    </span>);

}

export function riskTone(risk: RiskLevel | string): StatusTone {
  if (risk === 'High' || risk === 'HIGH') return 'critical';
  if (risk === 'Medium' || risk === 'MEDIUM' || risk === 'Elevated') return 'warning';
  return 'healthy';
}

export function RiskBadge({ risk }: {risk: RiskLevel | string;}) {
  return <Badge tone={riskTone(risk)}>{risk}</Badge>;
}