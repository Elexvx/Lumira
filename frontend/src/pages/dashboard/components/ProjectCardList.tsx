import { ArrowRightOutlined, CalendarOutlined, TeamOutlined } from '@ant-design/icons';
import { Avatar, Button, List, Space, Typography } from 'antd';
import { ProCard } from '@ant-design/pro-components';
import type { WorkbenchProject } from '../mock';

interface ProjectCardListProps {
  projects: WorkbenchProject[];
}

const ProjectCardList = ({ projects }: ProjectCardListProps) => {
  return (
    <ProCard
      className="saas-workbench-card"
      bordered={false}
      boxShadow
      title="进行中的项目"
      extra={
        <Button type="link" icon={<ArrowRightOutlined />} className="saas-workbench-card__link">
          全部项目
        </Button>
      }
    >
      <List
        className="saas-workbench-project-list"
        grid={{ gutter: 16, xs: 1, sm: 2, xl: 3 }}
        dataSource={projects}
        renderItem={(item) => {
          const Icon = item.icon;
          return (
            <List.Item>
              <ProCard hoverable className="saas-workbench-project-card" bordered boxShadow>
                <Space direction="vertical" size={12} className="saas-workbench-project-card__content">
                  <div className="saas-workbench-project-card__head">
                    <Avatar className="saas-workbench-project-card__avatar">
                      <Icon />
                    </Avatar>
                    <Typography.Title level={5} className="saas-workbench-project-card__title">
                      {item.name}
                    </Typography.Title>
                  </div>
                  <Typography.Paragraph type="secondary" className="saas-workbench-project-card__description">
                    {item.description}
                  </Typography.Paragraph>
                  <Space size={8} wrap className="saas-workbench-project-card__meta">
                    <Space size={4} className="saas-workbench-project-card__meta-item">
                      <TeamOutlined />
                      <Typography.Text type="secondary">{item.team}</Typography.Text>
                    </Space>
                    <Space size={4} className="saas-workbench-project-card__meta-item">
                      <CalendarOutlined />
                      <Typography.Text type="secondary">{item.updatedAt}</Typography.Text>
                    </Space>
                  </Space>
                </Space>
              </ProCard>
            </List.Item>
          );
        }}
      />
    </ProCard>
  );
};

export default ProjectCardList;
