import React from 'react';
import { Area, AreaChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

export const axisProps = {
  tick: { fill: '#94a3b8', fontSize: 11 },
  tickLine: false,
  axisLine: { stroke: '#e2e8f0' }
};

export const tooltipProps = {
  contentStyle: {
    borderRadius: 6,
    border: '1px solid #e2e8f0',
    boxShadow: '0 8px 24px rgba(11,37,69,0.12)',
    fontSize: 12
  },
  labelStyle: { color: '#0f172a', fontWeight: 600 }
};

interface TrendChartProps {
  data: Record<string, number | string>[];
  xKey: string;
  series: {key: string;name: string;color: string;}[];
  height?: number;
  domain?: [number | 'auto', number | 'auto'];
  area?: boolean;
  unit?: string;
}

export function TrendChart({ data, xKey, series, height = 200, domain = ['auto', 'auto'], area = false, unit }: TrendChartProps) {
  if (area) {
    return (
      <ResponsiveContainer width="100%" height={height}>
        <AreaChart data={data} margin={{ top: 8, right: 8, left: -18, bottom: 0 }}>
          <CartesianGrid stroke="#eef2f7" vertical={false} />
          <XAxis dataKey={xKey} {...axisProps} />
          <YAxis domain={domain} {...axisProps} unit={unit} width={48} />
          <Tooltip {...tooltipProps} />
          {series.map((s) =>
          <Area
            key={s.key}
            type="monotone"
            dataKey={s.key}
            name={s.name}
            stroke={s.color}
            fill={s.color}
            fillOpacity={0.1}
            strokeWidth={2}
            dot={false} />

          )}
        </AreaChart>
      </ResponsiveContainer>);

  }

  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={data} margin={{ top: 8, right: 8, left: -18, bottom: 0 }}>
        <CartesianGrid stroke="#eef2f7" vertical={false} />
        <XAxis dataKey={xKey} {...axisProps} />
        <YAxis domain={domain} {...axisProps} unit={unit} width={48} />
        <Tooltip {...tooltipProps} />
        {series.map((s) =>
        <Line
          key={s.key}
          type="monotone"
          dataKey={s.key}
          name={s.name}
          stroke={s.color}
          strokeWidth={2}
          dot={false}
          activeDot={{ r: 3 }} />

        )}
      </LineChart>
    </ResponsiveContainer>);

}