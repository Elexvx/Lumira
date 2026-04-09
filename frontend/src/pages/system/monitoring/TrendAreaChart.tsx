import { Area, type AreaConfig } from '@ant-design/charts';
import { Card, theme } from 'antd';

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

export default function TrendAreaChart({
  title,
  subtitle,
  points,
  color = DEFAULT_COLOR,
  height = 220,
  valueFormatter = (value) => value.toFixed(0),
}: TrendAreaChartProps) {
  const { token } = theme.useToken();
  const safePoints = points.length ? points : [{ label: '-', value: 0 }];
  const chartHeight = Math.max(140, height - 64);
  const config: AreaConfig = {
    data: safePoints,
    xField: 'label',
    yField: 'value',
    autoFit: true,
    height: chartHeight,
    tooltip: {
      showMarkers: true,
      shared: true,
    },
    axis: {
      x: {
        label: {
          autoHide: true,
          autoRotate: true,
        },
      },
      y: {
        label: {
          formatter: (value: string | number) => valueFormatter(Number(value)),
        },
      },
    },
    style: {
      shape: 'smooth',
      fill: color,
      fillOpacity: 0.28,
      stroke: color,
      strokeWidth: 2.5,
    },
    point: {
      size: 3.5,
      style: {
        fill: '#fff',
        stroke: color,
        lineWidth: 2,
      },
    },
    legend: false,
    padding: [8, 0, 20, 24],
  };

  return (
    <Card
      title={
        <div>
          <div style={{ fontSize: 16, fontWeight: 600, color: token.colorTextHeading }}>{title}</div>
          {subtitle ? <div style={{ marginTop: 4, fontSize: 12, color: token.colorTextSecondary }}>{subtitle}</div> : null}
        </div>
      }
      style={{
        height: '100%',
        overflow: 'hidden',
        borderRadius: token.borderRadiusLG,
      }}
    >
      <div style={{ height: chartHeight }}>
        <Area {...config} />
      </div>
    </Card>
  );
}
