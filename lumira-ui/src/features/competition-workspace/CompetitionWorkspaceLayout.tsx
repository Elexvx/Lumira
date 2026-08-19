import { AppstoreOutlined, AuditOutlined, BankOutlined, FileProtectOutlined, SafetyCertificateOutlined, SettingOutlined, TeamOutlined } from '@ant-design/icons';
import { Alert, Button, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useMemo } from 'react';
import { history, Link, Outlet, useLocation } from '@umijs/max';
import type { CompetitionWorkspaceModule } from '@/services/competition/types';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { useCompetitionWorkspace, CompetitionWorkspaceProvider } from './CompetitionWorkspaceContext';
import {
  competitionWorkspaceModuleFromPath,
  competitionWorkspacePath,
  COMPETITION_WORKSPACE_MODULES,
} from './competitionWorkspaceRoutes';
import { competitionWorkspaceStatusMeta } from './competitionWorkspacePresentation';
import './CompetitionWorkspaceLayout.css';

const moduleIcons: Record<CompetitionWorkspaceModule, React.ReactNode> = {
  overview: <AppstoreOutlined aria-hidden />,
  registrations: <TeamOutlined aria-hidden />,
  reviews: <AuditOutlined aria-hidden />,
  payments: <BankOutlined aria-hidden />,
  certificates: <SafetyCertificateOutlined aria-hidden />,
  settings: <SettingOutlined aria-hidden />,
  audit: <FileProtectOutlined aria-hidden />,
};

const WorkspaceFrame = () => {
  const location = useLocation();
  const { competitionUuid, workspace, loading, error, canOpen, navigateToModule, refresh } = useCompetitionWorkspace();
  const currentModule = competitionWorkspaceModuleFromPath(location.pathname);

  useEffect(() => {
    if (!loading && workspace && currentModule !== 'overview' && !canOpen(currentModule)) {
      navigateToModule('overview', true);
    }
  }, [canOpen, currentModule, loading, navigateToModule, workspace]);

  const visibleModules = useMemo(
    () => COMPETITION_WORKSPACE_MODULES.filter((item) => canOpen(item.key)),
    [canOpen],
  );

  if (loading) {
    return (
      <ManagementPageBody className="competition-workspace">
        <div className="competition-workspace__state"><Spin description="正在加载赛事工作空间…" /></div>
      </ManagementPageBody>
    );
  }

  if (error || !workspace || !competitionUuid) {
    return (
      <ManagementPageBody className="competition-workspace">
        <Alert
          type="error"
          showIcon
          title="赛事工作空间不可用"
          description={error?.message || '赛事不存在、已删除或当前账号无权访问。'}
          action={<Button onClick={refresh}>重新加载</Button>}
        />
      </ManagementPageBody>
    );
  }

  const statusMeta = competitionWorkspaceStatusMeta[workspace.status];
  const competitionNo = workspace.competitionNo?.trim() || '';
  const competitionCode = workspace.code?.trim() || '';
  const showDistinctCode = Boolean(competitionCode && competitionCode !== competitionNo);

  return (
    <ManagementPageBody className="competition-workspace">
      <section className="competition-workspace__shell" aria-label={`${workspace.title}赛事工作空间`}>
        <header className="competition-workspace__context">
          <div className="competition-workspace__identity">
            <Space className="competition-workspace__title-row" size={10} wrap>
              <Typography.Title level={3} className="competition-workspace__title">
                {workspace.title}
              </Typography.Title>
              <Tag color={statusMeta.color}>{statusMeta.label}</Tag>
            </Space>
            <Space className="competition-workspace__context-meta" size={[12, 4]} wrap separator={<span aria-hidden>·</span>}>
              <Typography.Text type="secondary">赛事编号 {competitionNo || '-'}</Typography.Text>
              {showDistinctCode ? <Typography.Text type="secondary">代码 {competitionCode}</Typography.Text> : null}
            </Space>
          </div>
          <Button
            className="competition-workspace__back-to-list"
            onClick={() => history.push('/competitions/management')}
          >
            返回列表
          </Button>
        </header>
        <nav className="competition-workspace__navigation" aria-label="赛事管理模块">
          <div className="competition-workspace__module-tabs">
            {visibleModules.map((item) => (
              <Link
                key={item.key}
                className={`competition-workspace__module-tab ${currentModule === item.key ? 'is-active' : ''}`}
                to={competitionWorkspacePath(competitionUuid, item.key)}
                aria-current={currentModule === item.key ? 'page' : undefined}
              >
                {moduleIcons[item.key]}
                <span>{item.label}</span>
              </Link>
            ))}
          </div>
        </nav>
        <main className="competition-workspace__module-content">
          <Outlet />
        </main>
      </section>
    </ManagementPageBody>
  );
};

const CompetitionWorkspaceLayout = () => (
  <CompetitionWorkspaceProvider>
    <ManagementPage
      title={false}
      content={null}
      ghost
      breadcrumbRender={false}
      className="competition-workspace-page"
    >
      <WorkspaceFrame />
    </ManagementPage>
  </CompetitionWorkspaceProvider>
);

export default CompetitionWorkspaceLayout;
