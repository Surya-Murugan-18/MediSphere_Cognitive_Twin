import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeftIcon } from 'lucide-react';

interface PageHeaderProps {
  title: string;
  subtitle?: React.ReactNode;
  meta?: React.ReactNode;
  actions?: React.ReactNode;
  backTo?: {to: string;label: string;};
}

export function PageHeader({ title, subtitle, meta, actions, backTo }: PageHeaderProps) {
  return (
    <header className="mb-5">
      {backTo &&
      <Link
        to={backTo.to}
        className="mb-2 inline-flex items-center gap-1.5 text-xs font-medium text-brand-600 transition-colors duration-150 ease-out hover:text-brand-700">
        
          <ArrowLeftIcon className="h-3.5 w-3.5" aria-hidden="true" />
          {backTo.label}
        </Link>
      }
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold tracking-tight text-slate-900 lg:text-2xl">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
          {meta && <div className="mt-3 flex flex-wrap items-center gap-2">{meta}</div>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </header>);

}