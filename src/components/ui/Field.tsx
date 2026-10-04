import React from 'react';
import { ChevronDownIcon } from 'lucide-react';

interface FieldProps {
  label: string;
  htmlFor: string;
  required?: boolean;
  error?: string;
  hint?: string;
  children: React.ReactNode;
  className?: string;
}

export function Field({ label, htmlFor, required, error, hint, children, className = '' }: FieldProps) {
  return (
    <div className={className}>
      <label htmlFor={htmlFor} className="mb-1.5 block text-xs font-medium text-slate-700">
        {label}
        {required && <span className="ml-0.5 text-critical-600">*</span>}
      </label>
      {children}
      {error ?
      <p className="mt-1 text-xs text-critical-600">{error}</p> :

      hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>
      }
    </div>);

}

const controlBase =
'w-full rounded-md border bg-white px-3 text-sm text-slate-900 placeholder:text-slate-400 transition-colors duration-150 ease-out focus:border-brand-500 focus:outline-none';

export function TextInput({
  invalid,
  className = '',
  ...props
}: React.InputHTMLAttributes<HTMLInputElement> & {invalid?: boolean;}) {
  return (
    <input
      className={`${controlBase} h-9 ${invalid ? 'border-critical-400' : 'border-slate-300'} ${className}`}
      aria-invalid={invalid || undefined}
      {...props} />);


}

export function Select({
  invalid,
  className = '',
  children,
  ...props
}: React.SelectHTMLAttributes<HTMLSelectElement> & {invalid?: boolean;}) {
  return (
    <div className="relative">
      <select
        className={`${controlBase} h-9 appearance-none pr-8 ${invalid ? 'border-critical-400' : 'border-slate-300'} ${className}`}
        aria-invalid={invalid || undefined}
        {...props}>
        
        {children}
      </select>
      <ChevronDownIcon className="pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
    </div>);

}

export function FilterSelect({
  label,
  value,
  onChange,
  options





}: {label: string;value: string;onChange: (value: string) => void;options: string[];}) {
  const id = `filter-${label.toLowerCase().replace(/\s+/g, '-')}`;
  return (
    <div className="min-w-[140px]">
      <label htmlFor={id} className="mb-1 block text-2xs font-semibold uppercase tracking-wide text-slate-500">
        {label}
      </label>
      <Select id={id} value={value} onChange={(e) => onChange(e.target.value)} className="h-8 text-xs">
        {options.map((option) =>
        <option key={option} value={option}>
            {option}
          </option>
        )}
      </Select>
    </div>);

}

export function Toggle({
  checked,
  onChange,
  label,
  description,
  id






}: {checked: boolean;onChange: (checked: boolean) => void;label: string;description?: string;id: string;}) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-slate-100 py-3 last:border-0">
      <div>
        <label htmlFor={id} className="text-sm font-medium text-slate-800">
          {label}
        </label>
        {description && <p className="mt-0.5 text-xs text-slate-500">{description}</p>}
      </div>
      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        onClick={() => onChange(!checked)}
        className={`relative mt-0.5 h-5 w-9 shrink-0 rounded-full border transition-colors duration-150 ease-out ${
        checked ? 'border-teal-600 bg-teal-600' : 'border-slate-300 bg-slate-200'}`
        }>
        
        <span
          className={`absolute top-0.5 h-3.5 w-3.5 rounded-full bg-white shadow-sm transition-transform duration-150 ease-out ${
          checked ? 'translate-x-4' : 'translate-x-0.5'}`
          } />
        
      </button>
    </div>);

}