import React from 'react';

interface TabsProps {
  tabs: {id: string;label: string;count?: number;}[];
  active: string;
  onChange: (id: string) => void;
  className?: string;
}

export function Tabs({ tabs, active, onChange, className = '' }: TabsProps) {
  return (
    <div className={`border-b border-slate-200 ${className}`} role="tablist">
      <div className="flex gap-1 overflow-x-auto">
        {tabs.map((tab) => {
          const isActive = tab.id === active;
          return (
            <button
              key={tab.id}
              role="tab"
              type="button"
              aria-selected={isActive}
              onClick={() => onChange(tab.id)}
              className={`-mb-px whitespace-nowrap border-b-2 px-3 py-2.5 text-sm font-medium transition-colors duration-150 ease-out ${
              isActive ?
              'border-brand-500 text-brand-700' :
              'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'}`
              }>
              
              {tab.label}
              {typeof tab.count === 'number' &&
              <span className="ml-1.5 rounded bg-slate-100 px-1.5 py-0.5 text-2xs font-semibold text-slate-600">{tab.count}</span>
              }
            </button>);

        })}
      </div>
    </div>);

}