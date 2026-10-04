import React from 'react';

interface CardProps {
  children: React.ReactNode;
  className?: string;
  as?: 'div' | 'section' | 'article';
}

export function Card({ children, className = '', as: Tag = 'section' }: CardProps) {
  return <Tag className={`rounded-lg border border-slate-200 bg-white shadow-card ${className}`}>{children}</Tag>;
}

interface CardHeaderProps {
  title: React.ReactNode;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  icon?: React.ReactNode;
  className?: string;
}

export function CardHeader({ title, description, actions, icon, className = '' }: CardHeaderProps) {
  return (
    <div className={`flex items-start justify-between gap-4 border-b border-slate-200 px-4 py-3 ${className}`}>
      <div className="flex min-w-0 items-start gap-2.5">
        {icon && <span className="mt-0.5 text-slate-400">{icon}</span>}
        <div className="min-w-0">
          <h2 className="truncate text-sm font-semibold text-slate-900">{title}</h2>
          {description && <p className="mt-0.5 text-xs text-slate-500">{description}</p>}
        </div>
      </div>
      {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
    </div>);

}

export function CardBody({ children, className = '' }: {children: React.ReactNode;className?: string;}) {
  return <div className={`p-4 ${className}`}>{children}</div>;
}

export function DefinitionRow({ label, value }: {label: string;value: React.ReactNode;}) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b border-slate-100 py-2 last:border-0">
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="text-right text-sm font-medium text-slate-900">{value}</dd>
    </div>);

}