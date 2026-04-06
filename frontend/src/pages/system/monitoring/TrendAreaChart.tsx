type Point = {
  label: string;
  value: number;
};

interface TrendAreaChartProps {
  title: string;
  subtitle?: string;
  points: Point[];
  color?: string;
  height?: number;
  valueFormatter?: (value: number) => string;
}

const DEFAULT_COLOR = '#4f7cff';

const buildPath = (points: Array<{ x: number; y: number }>, baselineY: number) => {
  if (!points.length) {
    return '';
  }
  const linePath = points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ');
  const first = points[0];
  const last = points[points.length - 1];
  return `${linePath} L ${last.x} ${baselineY} L ${first.x} ${baselineY} Z`;
};

export default function TrendAreaChart({
  title,
  subtitle,
  points,
  color = DEFAULT_COLOR,
  height = 220,
  valueFormatter = (value) => value.toFixed(0),
}: TrendAreaChartProps) {
  const width = 1000;
  const padding = { top: 22, right: 18, bottom: 34, left: 44 };
  const chartWidth = width - padding.left - padding.right;
  const chartHeight = height - padding.top - padding.bottom;
  const safePoints = points.length ? points : [{ label: '-', value: 0 }];
  const values = safePoints.map((point) => point.value);
  const min = Math.min(...values, 0);
  const max = Math.max(...values, 1);
  const spread = max - min || 1;

  const coordinates = safePoints.map((point, index) => {
    const x = safePoints.length === 1 ? padding.left + chartWidth / 2 : padding.left + (index / (safePoints.length - 1)) * chartWidth;
    const normalized = (point.value - min) / spread;
    const y = padding.top + chartHeight - normalized * chartHeight;
    return { x, y, label: point.label, value: point.value };
  });

  const areaPath = buildPath(coordinates, padding.top + chartHeight);
  const gridLines = Array.from({ length: 5 }, (_, index) => {
    const y = padding.top + (chartHeight / 4) * index;
    const labelValue = max - (spread / 4) * index;
    return { y, label: valueFormatter(labelValue) };
  });

  return (
    <div
      style={{
        background: 'linear-gradient(180deg, rgba(79, 124, 255, 0.08), rgba(79, 124, 255, 0.02))',
        border: '1px solid rgba(79, 124, 255, 0.12)',
        borderRadius: 20,
        padding: 20,
        boxShadow: '0 12px 30px rgba(15, 23, 42, 0.05)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 16, marginBottom: 16 }}>
        <div>
          <div style={{ fontSize: 16, fontWeight: 600, color: '#1f2937' }}>{title}</div>
          {subtitle ? <div style={{ fontSize: 12, color: 'rgba(31,41,55,0.58)', marginTop: 4 }}>{subtitle}</div> : null}
        </div>
      </div>
      <svg viewBox={`0 0 ${width} ${height}`} width="100%" height={height} preserveAspectRatio="none" style={{ display: 'block' }}>
        {gridLines.map((line) => (
          <g key={line.y}>
            <line x1={padding.left} x2={width - padding.right} y1={line.y} y2={line.y} stroke="rgba(15, 23, 42, 0.08)" strokeWidth={1} />
            <text x={padding.left - 10} y={line.y + 4} textAnchor="end" fontSize="12" fill="rgba(31,41,55,0.58)">
              {line.label}
            </text>
          </g>
        ))}

        {areaPath ? <path d={areaPath} fill={color} opacity={0.28} /> : null}
        {coordinates.length > 0 ? <path d={coordinates.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ')} fill="none" stroke={color} strokeWidth={2.5} /> : null}
        {coordinates.map((point, index) => (
          <g key={`${point.label}-${index}`}>
            <circle cx={point.x} cy={point.y} r={3.5} fill="#fff" stroke={color} strokeWidth={2} />
            {index % Math.max(1, Math.ceil(coordinates.length / 6)) === 0 || index === coordinates.length - 1 ? (
              <text x={point.x} y={height - 12} textAnchor="middle" fontSize="12" fill="rgba(31,41,55,0.58)">
                {point.label}
              </text>
            ) : null}
          </g>
        ))}
      </svg>
    </div>
  );
}
