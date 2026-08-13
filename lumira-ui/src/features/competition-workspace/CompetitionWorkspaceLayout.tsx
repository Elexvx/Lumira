import { AppstoreOutlined, AuditOutlined, BankOutlined, FileProtectOutlined, SafetyCertificateOutlined, SettingOutlined, TeamOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Layout, Menu, Select, Space, Spin, Tag, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { history, Outlet, useLocation } from '@umijs/max';
import { listCompetitionWorkspaces } from '@/services/competition/api';
import type { CompetitionWorkspaceModule, CompetitionWorkspaceRecord } from '@/services/competition/types';
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
  overview: <AppstoreOutlined />,
  registrations: <TeamOutlined />,
  reviews: <AuditOutlined />,
  payments: <BankOutlined />,
  certificates: <SafetyCertificateOutlined />,
  settings: <SettingOutlined />,
  audit: <FileProtectOutlined />,
};

const WorkspaceFrame = () => {
  const location = useLocation();
  const { competitionUuid, workspace, loading, error, canOpen, navigateToModule, refresh } = useCompetitionWorkspace();
  const [availableWorkspaces, setAvailableWorkspaces] = useState<CompetitionWorkspaceRecord[]>([]);
  const currentModule = competitionWorkspaceModuleFromPath(location.pathname);

  useEffect(() => {
    if (!workspace || !competitionUuid) return;
    let active = true;
    void listCompetitionWorkspaces({ pageNo: 1, pageSize: 50 }, { silent: true })
      .then((response) => {
        if (active) setAvailableWorkspaces(response.records || []);
      })
      .catch(() => {
        if (active) setAvailableWorkspaces([]);
      });
    return () => { active = false; };
  }, [competitionUuid, workspace]);

  useEffect(() => {
    if (!loading && workspace && currentModule !== 'overview' && !canOpen(currentModule)) {
      navigateToModule('overview', true);
    }
  }, [canOpen, currentModule, loading, navigateToModule, workspace]);

  const menuItems = useMemo<MenuProps['items']>(() => COMPETITION_WORKSPACE_MODULES
    .filter((item) => canOpen(item.key))
    .map((item) => ({ key: item.key, icon: moduleIcons[item.key], label: item.label })), [canOpen]);

  const workspaceOptions = useMemo(() => availableWorkspaces.map((item) => ({
    value: item.competitionUuid,
    label: `${item.title}${item.competitionNo ? `（${item.competitionNo}）` : ''}`,
  })), [availableWorkspaces]);

  if (loading) {
    return <ManagementPageBody className="competition-workspace"><Card><Spin description="正在加载赛事工作空间…" /></Card></ManagementPageBody>;
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

  const onMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (COMPETITION_WORKSPACE_MODULES.some((item) => item.key === key)) {
      navigateToModule(key as CompetitionWorkspaceModule);
    }
  };
  const statusMeta = competitionWorkspaceStatusMeta[workspace.status];

  return (
    <ManagementPageBody className="competition-workspace">
      <Card
        className="competition-workspace__card competition-workspace__card--unified"
        styles={{ body: { padding: 0 } }}
        title={(
          <Space wrap>
            <Typography.Text strong>{workspace.title}</Typography.Text>
            <Tag color={statusMeta.color}>{statusMeta.label}</Tag>
          </Space>
        )}
        extra={(
          <Select
            className="competition-workspace__selector"
            showSearch
            value={competitionUuid}
            options={workspaceOptions}
            placeholder="切换赛事"
            style={{ width: 'clamp(220px, 42vw, 420px)' }}
            optionFilterProp="label"
            onChange={(nextUuid) => history.push(competitionWorkspacePath(nextUuid, 'overview'))}
          />
        )}
      >
        <Menu
          className="competition-workspace__module-menu"
          mode="horizontal"
          selectedKeys={[currentModule]}
          items={menuItems}
          onClick={onMenuClick}
        />
        <div className="competition-workspace__module-content competition-workspace__module-content--unified">
          <Outlet />
        </div>
      </Card>
    </ManagementPageBody>
  );
};

const CompetitionWorkspaceLayout = () => (
  <CompetitionWorkspaceProvider>
    <Layout style={{ background: 'transparent' }}>
      <WorkspaceFrame />
    </Layout>
  </CompetitionWorkspaceProvider>
);

export default CompetitionWorkspaceLayout;
