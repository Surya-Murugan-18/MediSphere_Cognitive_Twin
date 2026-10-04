import React from 'react';
import type { StatusTone } from '../../types/clinical';

const dotTones: Record<StatusTone, string> = {
  healthy: 'bg-teal-500',
  warning: 'bg-amber-500',
  critical: 'bg-critical-500',
  info: 'bg-brand-500',
  neutral: 'bg-slate-300'
};

export interface TimelineItem {
  id: string;
  timestamp: string;
  title: string;
  detail: string;
  tone: StatusTone;
}

export function Timeline({ items }: {items: TimelineItem[];}) {
  return (
    <ol className="relative space-y-4 pl-5">
      <span className="absolute left-[5px] top-1.5 bottom-1.5 w-px bg-slate-200" aria-hidden="true" />
      {items.map((item) =>
      <li key={item.id} className="relative">
          <span className={`absolute -left-5 top-1.5 h-2.5 w-2.5 rounded-full ring-2 ring-white ${dotTones[item.tone]}`} aria-hidden="true" />
          <p className="text-2xs font-medium uppercase tracking-wide text-slate-400">{item.timestamp}</p>
          <p className="text-sm font-medium text-slate-900">{item.title}</p>
          <p className="mt-0.5 text-xs leading-relaxed text-slate-500">{item.detail}</p>
        </li>
      )}
    </ol>);

}