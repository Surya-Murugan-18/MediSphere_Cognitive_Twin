import React from 'react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { tooltipProps } from './TrendChart';

interface RiskDonutProps {
  data: {name: string;value: number;tone: string;}[];
  centerLabel: string;
  centerValue: string;
  height?: number;
}

export function RiskDonut({ data, centerLabel, centerValue, height = 200 }: RiskDonutProps) {
  const total = data.reduce((sum, d) => sum + d.value, 0);
  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
      <div className="relative" style={{ width: height, height }}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={data} dataKey="value" innerRadius="66%" outerRadius="94%" paddingAngle={2} stroke="none">
              {data.map((entry, index) =>
              <Cell key={index} fill={entry.tone} />
              )}
            </Pie>
            <Tooltip {...tooltipProps} />
          </PieChart>
        </ResponsiveContainer>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-2xl font-semibold tabular text-slate-900">{centerValue}</span>
          <span className="text-2xs uppercase tracking-wide text-slate-500">{centerLabel}</span>
        </div>
      </div>
      <ul className="flex-1 space-y-2">
        {data.map((entry) =>
        <li key={entry.name} className="flex items-center justify-between gap-3 text-sm">
            <span className="flex items-center gap-2 text-slate-600">
              <span className="h-2.5 w-2.5 rounded-sm" style={{ background: entry.tone }} aria-hidden="true" />
              {entry.name} risk
            </span>
            <span className="tabular text-slate-900">
              {entry.value.toLocaleString()}
              <span className="ml-1.5 text-xs text-slate-400">{total ? Math.round(entry.value / total * 100) : 0}%</span>
            </span>
          </li>
        )}
      </ul>
    </div>);

}