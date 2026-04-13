import { ClockCircleOutlined } from '@ant-design/icons';
import { Avatar, List, Space, Typography } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import type { WorkbenchActivity } from '../mock';

interface ActivityFeedProps {
  items: WorkbenchActivity[];
}

const ActivityFeed = ({ items }: ActivityFeedProps) => {
  return (
    <ProCard className="saas-workbench-card" bordered={false} boxShadow title="动态">
      <List
        className="saas-workbench-activity-list"
        dataSource={items}
        split={false}
        renderItem={(item) => (
          <List.Item className="saas-workbench-activity-list__item">
            <List.Item.Meta
              avatar={
                <Avatar style={{ backgroundColor: item.avatarColor }} className="saas-workbench-activity-list__avatar">
                  {item.avatarText}
                </Avatar>
              }
              title={
                <Space size={6} wrap>
                  <Typography.Text strong>{item.userName}</Typography.Text>
                  <Typography.Text type="secondary">{item.action}</Typography.Text>
                  <Typography.Text className="saas-workbench-activity-list__target">{item.target}</Typography.Text>
                </Space>
              }
              description={
                <Space size={4} className="saas-workbench-activity-list__time">
                  <ClockCircleOutlined />
                  <Typography.Text type="secondary">{item.time}</Typography.Text>
                </Space>
              }
            />
          </List.Item>
        )}
      />
    </ProCard>
  );
};

export default ActivityFeed;
