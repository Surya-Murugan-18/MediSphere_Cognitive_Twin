import React from 'react';

export function TableShell({ children, className = '' }: {children: React.ReactNode;className?: string;}) {
  return (
    <div className={`overflow-x-auto ${className}`}>
      <table className="w-full min-w-[720px] border-collapse text-left text-sm">{children}</table>
    </div>);

}

export function Th({ children, className = '', align = 'left' }: {children?: React.ReactNode;className?: string;align?: 'left' | 'right' | 'center';}) {
  return (
    <th
      scope="col"
      className={`whitespace-nowrap border-b border-slate-200 bg-slate-50 px-3 py-2 text-2xs font-semibold uppercase tracking-wide text-slate-500 ${
      align === 'right' ? 'text-right' : align === 'center' ? 'text-center' : 'text-left'} ${
      className}`}>
      
      {children}
    </th>);

}

export function Td({ children, className = '', align = 'left' }: {children?: React.ReactNode;className?: string;align?: 'left' | 'right' | 'center';}) {
  return (
    <td
      className={`border-b border-slate-100 px-3 py-2.5 text-slate-700 ${
      align === 'right' ? 'text-right' : align === 'center' ? 'text-center' : 'text-left'} ${
      className}`}>
      
      {children}
    </td>);

}

export function Tr({
  children,
  onClick,
  className = ''




}: {children: React.ReactNode;onClick?: () => void;className?: string;}) {
  if (onClick) {
    return (
      <tr
        tabIndex={0}
        role="button"
        onClick={onClick}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onClick();
          }
        }}
        className={`cursor-pointer transition-colors duration-150 ease-out hover:bg-brand-50/60 ${className}`}>
        
        {children}
      </tr>);

  }
  return <tr className={className}>{children}</tr>;
}