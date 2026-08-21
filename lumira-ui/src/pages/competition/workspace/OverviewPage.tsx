import {
  ArrowRightOutlined,
  AuditOutlined,
  BankOutlined,
  FileProtectOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Col, Empty, Row, Tag, Typography } from 'antd';
import type { CompetitionWorkspaceModule } from '@/services/competition/types';
import { useCompetitionWorkspace } from '@/features/competition-workspace/CompetitionWorkspaceContext';
import { CompetitionWorkspacePageFrame } from '@/features/competition-workspace/CompetitionWorkspacePageFrame';
import { competitionWorkspaceStatusMeta } from '@/features/competition-workspace/competitionWorkspacePresentation';
import { COMPETITION_WORKSPACE_MODULES } from '@/features/competition-workspace/competitionWorkspaceRoutes';

const OverviewPage = () => {
  const { workspace, canOpen, navigateToModule } = useCompetitionWorkspace();
  if (!workspace) return null;

  const modules = COMPETITION_WORKSPACE_MODULES.filter((item) => item.key !== 'overview' && canOpen(item.key));
  const statusMeta = competitionWorkspaceStatusMeta[workspace.status];
  const modulePresentation: Record<Exclude<CompetitionWorkspaceModule, 'overview'>, {
    icon: React.ReactNode;
    description: string;
  }> = {
    registrations: { icon: <TeamOutlined />, description: '筛选报名团队、核对完整资料并按范围导出。' },
    reviews: { icon: <AuditOutlined />, description: '配置评审方案与批次，推进签到、评分和结果发布。' },
    payments: { icon: <BankOutlined />, description: '追踪订单、支付渠道与报名付款状态。' },
    certificates: { icon: <SafetyCertificateOutlined />, description: '从评审结果制证，并管理批次、下载与撤销。' },
    settings: { icon: <SettingOutlined />, description: '维护赛事基础信息、报名规则、赛程材料和费用。' },
    audit: { icon: <FileProtectOutlined />, description: '查看当前赛事的重要配置变更与操作记录。' },
  };

  return (
    <CompetitionWorkspacePageFrame
      embeddedInWorkspace
      title="赛事概览"
      workspaceVariant="content"
    >
      <div className="competition-workspace-overview">
        <section className="competition-workspace-overview__metrics" aria-label="赛事核心指标">
          <div className="competition-workspace-overview__metric">
            <Typography.Text type="secondary">当前状态</Typography.Text>
            <span className="competition-workspace-overview__metric-value">
              <Tag color={statusMeta.color}>{statusMeta.label}</Tag>
            </span>
          </div>
          <div className="competition-workspace-overview__metric">
            <Typography.Text type="secondary">有效报名</Typography.Text>
            <Typography.Text className="competition-workspace-overview__metric-value">
              {workspace.activeRegistrationCount ?? 0}
            </Typography.Text>
          </div>
          <div className="competition-workspace-overview__metric">
            <Typography.Text type="secondary">可管理模块</Typography.Text>
            <Typography.Text className="competition-workspace-overview__metric-value">
              {modules.length}
            </Typography.Text>
          </div>
        </section>

        <section className="competition-workspace-overview__section" aria-labelledby="competition-workspace-details-title">
          <div className="competition-workspace-overview__section-heading">
            <div>
              <Typography.Title id="competition-workspace-details-title" level={4} className="competition-workspace-overview__section-title">
                赛事信息
              </Typography.Title>
            </div>
          </div>
          <dl className="competition-workspace-overview__details">
            <div className="competition-workspace-overview__detail competition-workspace-overview__detail--wide">
              <dt>赛事名称</dt>
              <dd>{workspace.title || '-'}</dd>
            </div>
            <div className="competition-workspace-overview__detail">
              <dt>赛事编号</dt>
              <dd>{workspace.competitionNo || '-'}</dd>
            </div>
            <div className="competition-workspace-overview__detail">
              <dt>赛事代码</dt>
              <dd>{workspace.code || '-'}</dd>
            </div>
            <div className="competition-workspace-overview__detail competition-workspace-overview__detail--wide">
              <dt>工作区 UUID</dt>
              <dd>{workspace.competitionUuid}</dd>
            </div>
          </dl>
        </section>

        <section className="competition-workspace-overview__section" aria-labelledby="competition-workspace-actions-title">
          <div className="competition-workspace-overview__section-heading">
            <div>
              <Typography.Title id="competition-workspace-actions-title" level={4} className="competition-workspace-overview__section-title">
                快捷操作
              </Typography.Title>
            </div>
          </div>
          {modules.length ? (
            <Row gutter={[16, 16]}>
              {modules.map((module) => {
                const presentation = modulePresentation[module.key as Exclude<CompetitionWorkspaceModule, 'overview'>];
                return (
                  <Col xs={24} md={12} xl={8} key={module.key}>
                    <button
                      className="competition-workspace-overview__module-tile"
                      type="button"
                      aria-label={`进入${module.label}`}
                      onClick={() => navigateToModule(module.key)}
                    >
                      <span className="competition-workspace-overview__module-icon" aria-hidden>{presentation.icon}</span>
                      <span className="competition-workspace-overview__module-copy">
                        <Typography.Text strong>{module.label}</Typography.Text>
                        <Typography.Paragraph type="secondary" className="competition-workspace-overview__module-description">
                          {presentation.description}
                        </Typography.Paragraph>
                      </span>
                      <ArrowRightOutlined className="competition-workspace-overview__module-arrow" aria-hidden />
                    </button>
                  </Col>
                );
              })}
            </Row>
          ) : <Empty description="当前账号没有可用模块" />}
        </section>
      </div>
    </CompetitionWorkspacePageFrame>
  );
};

export default OverviewPage;
