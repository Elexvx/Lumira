import { Avatar, Col, Row, Typography } from 'antd';
import { StatisticCard } from '@ant-design/pro-components';
import type { WelcomeMetric, WelcomeProfile } from '../mock';

interface WelcomeCardProps {
  profile: WelcomeProfile;
  metrics: WelcomeMetric[];
}

const WelcomeCard = ({ profile, metrics }: WelcomeCardProps) => {
  return (
    <div className="saas-workbench-card saas-workbench-welcome-card">
      <Row gutter={[24, 24]} align="middle">
        <Col xs={24} lg={13}>
          <div className="saas-workbench-welcome-card__identity">
            <Avatar
              size={64}
              className="saas-workbench-welcome-card__avatar"
              style={{ background: profile.avatarBackground }}
            >
              {profile.avatarText}
            </Avatar>
            <div className="saas-workbench-welcome-card__copy">
              <Typography.Text type="secondary">{profile.greeting}</Typography.Text>
              <Typography.Title level={2} className="saas-workbench-welcome-card__title">
                {profile.name}
              </Typography.Title>
              <Typography.Text className="saas-workbench-welcome-card__meta">
                {profile.role} · {profile.department}
              </Typography.Text>
              <Typography.Paragraph type="secondary" className="saas-workbench-welcome-card__note">
                {profile.note}
              </Typography.Paragraph>
            </div>
          </div>
        </Col>
        <Col xs={24} lg={11}>
          <Row gutter={[16, 16]} className="saas-workbench-welcome-card__stats">
            {metrics.map((item) => (
              <Col key={item.label} xs={24} sm={8}>
                <StatisticCard
                  className="saas-workbench-welcome-card__stat"
                  bordered={false}
                  bodyStyle={{ padding: 0 }}
                  statistic={{
                    title: item.label,
                    value: item.value,
                    description: <Typography.Text type="secondary">{item.trend}</Typography.Text>,
                    valueStyle: { fontSize: 24, fontWeight: 700 },
                  }}
                />
              </Col>
            ))}
          </Row>
        </Col>
      </Row>
    </div>
  );
};

export default WelcomeCard;
