import { PageContainer } from '@ant-design/pro-components';
import { Col, Row, Space } from 'antd';
import ActivityFeed from './components/ActivityFeed';
import ProjectCardList from './components/ProjectCardList';
import QuickActionsCard from './components/QuickActionsCard';
import RadarIndexCard from './components/RadarIndexCard';
import TeamCard from './components/TeamCard';
import WelcomeCard from './components/WelcomeCard';
import {
  welcomeMetrics,
  welcomeProfile,
  workbenchActivities,
  workbenchProjects,
  workbenchRadarData,
  workbenchShortcutSlots,
  workbenchTeams,
} from './mock';
import './Home.less';

const DashboardHomePage = () => {
  return (
    <PageContainer className="saas-dashboard-workplace" ghost title="工作台" content={null} style={{ minHeight: '100%' }}>
      <div className="saas-management-page-body saas-workbench-page__body">
        <WelcomeCard profile={welcomeProfile} metrics={welcomeMetrics} />

        <Row gutter={[16, 16]} align="stretch">
          <Col xs={24} xl={16}>
            <Space direction="vertical" size={16} className="saas-workbench-page__stack">
              <ProjectCardList projects={workbenchProjects} />
              <ActivityFeed items={workbenchActivities} />
            </Space>
          </Col>

          <Col xs={24} xl={8}>
            <Space direction="vertical" size={16} className="saas-workbench-page__stack">
              <QuickActionsCard placeholders={workbenchShortcutSlots} />
              <RadarIndexCard data={workbenchRadarData} />
              <TeamCard teams={workbenchTeams} />
            </Space>
          </Col>
        </Row>
      </div>
    </PageContainer>
  );
};

export default DashboardHomePage;
