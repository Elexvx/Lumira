import { AuditOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import { Tag, Typography } from 'antd';
import { useMemo } from 'react';
import { useCompetitionWorkspace } from '@/features/competition-workspace/CompetitionWorkspaceContext';
import { CompetitionWorkspacePageFrame } from '@/features/competition-workspace/CompetitionWorkspacePageFrame';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useResponsive } from '@/hooks/useResponsive';
import { listCompetitionWorkspaceAudit } from '@/services/competition/api';
import type { CompetitionAuditRecord } from '@/services/competition/types';

const AuditPage = () => {
  const { competitionUuid, workspace } = useCompetitionWorkspace();
  const responsive = useResponsive();
  const columns = useMemo<ProColumns<CompetitionAuditRecord>[]>(() => [
    {
      title: '时间',
      dataIndex: 'createdAt',
      search: false,
      width: 180,
      render: (value) => value || '-',
    },
    {
      title: '模块',
      dataIndex: 'module',
      width: 160,
      render: (value) => <Tag color="blue">{value || '-'}</Tag>,
    },
    {
      title: '动作',
      dataIndex: 'action',
      width: 160,
    },
    {
      title: '详情',
      dataIndex: 'detailMessage',
      search: false,
      ellipsis: true,
      render: (value) => <Typography.Text>{value || '-'}</Typography.Text>,
    },
    {
      title: '操作人 UUID',
      dataIndex: 'operatorUserUuid',
      search: false,
      width: 260,
      ellipsis: true,
      render: (value) => value || '-',
    },
  ], []);

  if (!competitionUuid || !workspace) return null;

  return (
    <CompetitionWorkspacePageFrame
      embeddedInWorkspace
      title={<span><AuditOutlined /> 赛事审计</span>}
      description={<>仅展示当前赛事（{workspace.title}）的配置变更审计记录。</>}
      showWorkspaceHeader
      workspaceVariant="table"
    >
      <ManagementTable<CompetitionAuditRecord>
        rowKey="id"
        columns={columns}
        isMobile={responsive.isMobile}
        search={false}
        request={async (params) => {
          const response = await listCompetitionWorkspaceAudit(competitionUuid, {
            module: typeof params.module === 'string' ? params.module : undefined,
            pageNo: params.current,
            pageSize: params.pageSize,
          });
          return { data: response.records, total: response.total, success: true };
        }}
        pagination={{ pageSize: 20, showSizeChanger: true }}
      />
    </CompetitionWorkspacePageFrame>
  );
};

export default AuditPage;
