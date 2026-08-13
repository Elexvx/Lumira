import { Button, Card, Col, Descriptions, Empty, Row, Space, Tag, Typography } from 'antd';
import { useCompetitionWorkspace } from '@/features/competition-workspace/CompetitionWorkspaceContext';
import { CompetitionWorkspacePageFrame } from '@/features/competition-workspace/CompetitionWorkspacePageFrame';
import { competitionWorkspaceStatusMeta } from '@/features/competition-workspace/competitionWorkspacePresentation';
import { COMPETITION_WORKSPACE_MODULES } from '@/features/competition-workspace/competitionWorkspaceRoutes';

const OverviewPage = () => {
  const { workspace, canOpen, navigateToModule } = useCompetitionWorkspace();
  if (!workspace) return null;

  const modules = COMPETITION_WORKSPACE_MODULES.filter((item) => canOpen(item.key));
  const statusMeta = competitionWorkspaceStatusMeta[workspace.status];
  return (
    <CompetitionWorkspacePageFrame embeddedInWorkspace title="概览" workspaceVariant="content">
      <Space direction="vertical" size={16} style={{ display: 'flex' }}>
        <Card title="概览">
          <Descriptions column={{ xs: 1, sm: 2, md: 3 }}>
            <Descriptions.Item label="赛事编号">{workspace.competitionNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="赛事名称">{workspace.title || '-'}</Descriptions.Item>
            <Descriptions.Item label="赛事代码">{workspace.code || '-'}</Descriptions.Item>
            <Descriptions.Item label="外部身份">{workspace.competitionUuid}</Descriptions.Item>
            <Descriptions.Item label="当前状态">
              <Tag color={statusMeta.color}>{statusMeta.label}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="有效报名数">{workspace.activeRegistrationCount ?? 0}</Descriptions.Item>
          </Descriptions>
        </Card>
        <Card title="可用模块">
          {modules.length ? (
            <Row gutter={[16, 16]}>
              {modules.map((module) => (
                <Col xs={24} sm={12} lg={8} key={module.key}>
                  <Card
                    className="competition-workspace-overview__module-card"
                    size="small"
                    title={module.label}
                  >
                    <Typography.Paragraph type="secondary" ellipsis={{ rows: 2 }}>
                      所有数据请求都绑定当前赛事 UUID，并由服务端重新校验访问范围。
                    </Typography.Paragraph>
                    <Button
                      className="competition-workspace-overview__module-action"
                      type="link"
                      onClick={() => navigateToModule(module.key)}
                    >
                      进入模块
                    </Button>
                  </Card>
                </Col>
              ))}
            </Row>
          ) : <Empty description="当前账号没有可用模块" />}
        </Card>
      </Space>
    </CompetitionWorkspacePageFrame>
  );
};

export default OverviewPage;
