import React, { useState } from 'react';

export interface BodyRegion {
  id: string;
  label: string;
  detail: string;
  level: 'high' | 'medium' | 'low';
  cx: number;
  cy: number;
  r: number;
}

const levelFill: Record<BodyRegion['level'], string> = {
  high: '#d13f3f',
  medium: '#d98a00',
  low: '#0e9f7e'
};

export const defaultRegions: BodyRegion[] = [
{ id: 'heart', label: 'Cardiac', detail: '10-year CVD risk 24.3% — elevated', level: 'high', cx: 88, cy: 118, r: 16 },
{ id: 'vascular', label: 'Vascular / Blood Pressure', detail: 'BP 142/91 mmHg above target', level: 'high', cx: 112, cy: 150, r: 14 },
{ id: 'pancreas', label: 'Metabolic', detail: 'HbA1c 8.2% — diabetes complication risk 31.8%', level: 'medium', cx: 92, cy: 166, r: 13 },
{ id: 'kidney', label: 'Renal', detail: 'Creatinine 0.9 mg/dL — within range', level: 'low', cx: 118, cy: 182, r: 11 },
{ id: 'lungs', label: 'Respiratory', detail: 'SpO₂ 98% — normal', level: 'low', cx: 118, cy: 112, r: 12 }];


export function BodyMap({ regions = defaultRegions }: {regions?: BodyRegion[];}) {
  const [activeId, setActiveId] = useState<string>('heart');
  const active = regions.find((r) => r.id === activeId) ?? regions[0];

  return (
    <div className="flex flex-col gap-5 lg:flex-row">
      <div className="relative mx-auto w-full max-w-[280px] rounded-lg border border-slate-200 bg-slate-50/70 p-4">
        <svg viewBox="0 0 200 400" className="h-[340px] w-full" role="img" aria-label="Digital twin body risk heatmap">
          <g fill="#dbe4ee" stroke="#c3d0de" strokeWidth="1.5">
            <circle cx="100" cy="42" r="26" />
            <rect x="92" y="66" width="16" height="14" rx="6" />
            <path d="M68 82 h64 a14 14 0 0 1 14 14 v76 a12 12 0 0 1 -12 12 h-68 a12 12 0 0 1 -12 -12 v-76 a14 14 0 0 1 14 -14 z" />
            <rect x="46" y="86" width="18" height="96" rx="9" />
            <rect x="136" y="86" width="18" height="96" rx="9" />
            <rect x="78" y="184" width="18" height="110" rx="9" />
            <rect x="104" y="184" width="18" height="110" rx="9" />
            <rect x="80" y="296" width="14" height="66" rx="7" />
            <rect x="106" y="296" width="14" height="66" rx="7" />
          </g>
          {regions.map((region) => {
            const isActive = region.id === activeId;
            return (
              <g key={region.id}>
                <circle cx={region.cx} cy={region.cy} r={region.r * 1.9} fill={levelFill[region.level]} opacity={isActive ? 0.18 : 0.1} />
                <circle
                  cx={region.cx}
                  cy={region.cy}
                  r={region.r}
                  fill={levelFill[region.level]}
                  opacity={isActive ? 0.85 : 0.55}
                  stroke="#ffffff"
                  strokeWidth={isActive ? 2 : 1}
                  className="cursor-pointer transition-opacity duration-150 ease-out"
                  tabIndex={0}
                  role="button"
                  aria-label={`${region.label} risk region`}
                  onClick={() => setActiveId(region.id)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') setActiveId(region.id);
                  }} />
                
              </g>);

          })}
        </svg>
        <p className="mt-2 text-center text-2xs text-slate-400">Risk heatmap · select a region for detail</p>
      </div>

      <div className="flex-1">
        <div className="rounded-lg border border-slate-200 bg-white p-3">
          <p className="text-2xs font-semibold uppercase tracking-wide text-slate-500">Selected region</p>
          <p className="mt-1 text-sm font-semibold text-slate-900">{active.label}</p>
          <p className="mt-0.5 text-xs text-slate-600">{active.detail}</p>
        </div>
        <ul className="mt-3 space-y-1.5">
          {regions.map((region) =>
          <li key={region.id}>
              <button
              type="button"
              onClick={() => setActiveId(region.id)}
              className={`flex w-full items-center justify-between gap-3 rounded-md border px-3 py-2 text-left text-sm transition-colors duration-150 ease-out ${
              region.id === activeId ? 'border-brand-300 bg-brand-50' : 'border-slate-200 bg-white hover:bg-slate-50'}`
              }>
              
                <span className="flex items-center gap-2 text-slate-700">
                  <span className="h-2 w-2 rounded-full" style={{ background: levelFill[region.level] }} aria-hidden="true" />
                  {region.label}
                </span>
                <span className="text-2xs font-semibold uppercase tracking-wide text-slate-500">{region.level}</span>
              </button>
            </li>
          )}
        </ul>
      </div>
    </div>);

}