import React from 'react';
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { axisProps, tooltipProps } from './TrendChart';

interface CategoryBarChartProps {
  data: {name: string;value: number;tone?: string;}[];
  height?: number;
  layout?: 'horizontal' | 'vertical';
  color?: string;
}

export function CategoryBarChart({ data, height = 220, layout = 'horizontal', color = '#1f6fd0' }: CategoryBarChartProps) {
  const vertical = layout === 'vertical';
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart
        data={data}
        layout={vertical ? 'vertical' : 'horizontal'}
        margin={{ top: 8, right: 12, left: vertical ? 24 : -18, bottom: 0 }}
        barSize={vertical ? 14 : 28}>
        
        <CartesianGrid stroke="#eef2f7" vertical={vertical} horizontal={!vertical} />
        {vertical ?
        <>
            <XAxis type="number" {...axisProps} />
            <YAxis type="category" dataKey="name" width={96} {...axisProps} />
          </> :

        <>
            <XAxis dataKey="name" {...axisProps} />
            <YAxis {...axisProps} width={48} />
          </>
        }
        <Tooltip {...tooltipProps} cursor={{ fill: '#f1f5f9' }} />
        <Bar dataKey="value" radius={[3, 3, 0, 0]}>
          {data.map((entry, index) =>
          <Cell key={index} fill={entry.tone ?? color} />
          )}
        </Bar>
      </BarChart>
    </ResponsiveContainer>);

}