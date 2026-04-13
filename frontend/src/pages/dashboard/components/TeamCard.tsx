import { List, Space, Typography } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import type { WorkbenchTeam } from '../mock';

interface TeamCardProps {
  teams: WorkbenchTeam[];
}

const TeamCard = ({ teams }: TeamCardProps) => {
  return (
    <ProCard className="saas-workbench-card" bordered={false} boxShadow title="团队">
      <List
        className="saas-workbench-team-list"
        dataSource={teams}
        renderItem={(item) => {
          const Icon = item.icon;
          return (
            <List.Item className="saas-workbench-team-list__item">
              <Space size={12} align="start" className="saas-workbench-team-list__content">
                <div className="saas-workbench-team-list__icon">
                  <Icon />
                </div>
                <div className="saas-workbench-team-list__copy">
                  <Typography.Text strong>{item.name}</Typography.Text>
                  <Typography.Text type="secondary" className="saas-workbench-team-list__description">
                    {item.description}
                  </Typography.Text>
                </div>
              </Space>
              <Typography.Text type="secondary">{item.members}</Typography.Text>
            </List.Item>
          );
        }}
      />
    </ProCard>
  );
};

export default TeamCard;
