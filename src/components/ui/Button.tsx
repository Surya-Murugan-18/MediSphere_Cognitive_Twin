import React from 'react';
import { Link } from 'react-router-dom';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'approve';
type Size = 'sm' | 'md' | 'lg';

const variants: Record<Variant, string> = {
  primary: 'bg-brand-500 text-white border border-brand-500 hover:bg-brand-600 hover:border-brand-600',
  secondary: 'bg-white text-slate-700 border border-slate-300 hover:bg-slate-50 hover:border-slate-400',
  ghost: 'bg-transparent text-brand-600 border border-transparent hover:bg-brand-50',
  danger: 'bg-critical-500 text-white border border-critical-500 hover:bg-critical-600 hover:border-critical-600',
  approve: 'bg-teal-600 text-white border border-teal-600 hover:bg-teal-700 hover:border-teal-700'
};

const sizes: Record<Size, string> = {
  sm: 'h-8 px-3 text-xs',
  md: 'h-9 px-3.5 text-sm',
  lg: 'h-11 px-5 text-sm'
};

const base =
'inline-flex items-center justify-center gap-2 rounded-md font-medium whitespace-nowrap transition-colors duration-150 ease-out disabled:opacity-50 disabled:pointer-events-none';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

export function Button({ variant = 'secondary', size = 'md', className = '', ...props }: ButtonProps) {
  return <button className={`${base} ${variants[variant]} ${sizes[size]} ${className}`} {...props} />;
}

interface LinkButtonProps {
  to: string;
  variant?: Variant;
  size?: Size;
  className?: string;
  children: React.ReactNode;
}

export function LinkButton({ to, variant = 'secondary', size = 'md', className = '', children }: LinkButtonProps) {
  return (
    <Link to={to} className={`${base} ${variants[variant]} ${sizes[size]} ${className}`}>
      {children}
    </Link>);

}