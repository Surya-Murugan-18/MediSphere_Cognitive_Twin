import React from 'react';
import { SparklesIcon } from 'lucide-react';

type NoticeKind = 'prediction' | 'carePlan' | 'alert';

const copy: Record<NoticeKind, string> = {
  prediction: 'AI risk prediction — clinical review required. Output is decision support, not a diagnosis.',
  carePlan: 'AI-generated recommendation — requires provider review before any medication or care change.',
  alert: 'Possible abnormal condition detected. Clinician assessment required before acting.'
};

export function AiNotice({ kind, className = '' }: {kind: NoticeKind;className?: string;}) {
  return (
    <p
      className={`flex items-start gap-2 rounded-md border border-brand-100 bg-brand-50 px-3 py-2 text-xs leading-relaxed text-brand-800 ${className}`}
      role="note">
      
      <SparklesIcon className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      <span>{copy[kind]}</span>
    </p>);

}

export function DemoDataNote({ className = '' }: {className?: string;}) {
  return (
    <p className={`text-2xs text-slate-400 ${className}`}>
      Demonstration dataset — values illustrate platform behaviour and are not real clinical measurements.
    </p>);

}