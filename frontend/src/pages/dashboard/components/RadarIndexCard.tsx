import { Radar } from '@ant-design/charts';
import { Typography } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import type { WorkbenchRadarPoint } from '../mock';

interface RadarIndexCardProps {
  data: WorkbenchRadarPoint[];
}

const RadarIndexCard = ({ data }: RadarIndexCardProps) => {
  return (
    <ProCard
      className="saas-workbench-card saas-workbench-radar-card"
      bordered={false}
      boxShadow
      title="XX 指数"
      extra={<Typography.Text type="secondary">本地 mock 雷达图</Typography.Text>}
    >
      <div className="saas-workbench-radar-card__chart">
        <Radar
          data={data}
          xField="indicator"
          yField="value"
          meta={{
            value: {
              min: 0,
              max: 100,
            },
          }}
          legend={false}
          area
          point={{ size: 3 }}
          appendPadding={[12, 20, 12, 20]}
          height={280}
          autoFit
        />
      </div>
    </ProCard>
  );
};

export default RadarIndexCard;
