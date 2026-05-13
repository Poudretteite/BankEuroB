import type { FC } from 'react';
import {
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
  Legend
} from 'recharts';

interface ChartData {
  name: string;
  Wpływy: number;
  Wydatki: number;
}

interface ChartProps {
  data: ChartData[];
}

const Chart: FC<ChartProps> = ({ data }) => {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} margin={{ top: 10, right: 0, left: -20, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
        <XAxis dataKey="name" stroke="var(--text-secondary)" fontSize={12} tickLine={false} axisLine={false} />
        <YAxis stroke="var(--text-secondary)" fontSize={12} tickLine={false} axisLine={false} />
        <Tooltip
          contentStyle={{ backgroundColor: 'var(--bg-secondary)', border: '1px solid var(--glass-border)', borderRadius: '8px' }}
          itemStyle={{ color: 'var(--text-primary)' }}
          cursor={{ fill: 'rgba(255,255,255,0.05)' }}
        />
        <Legend iconType="circle" wrapperStyle={{ fontSize: '12px', paddingTop: '10px' }} />
        <Bar dataKey="Wpływy" fill="var(--success-color)" radius={[4, 4, 0, 0]} />
        <Bar dataKey="Wydatki" fill="#e74c3c" radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
};

export default Chart;
