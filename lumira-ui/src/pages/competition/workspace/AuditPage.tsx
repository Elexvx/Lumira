import { AuditOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import { Tag, Typography } from 'antd';
import { useMemo } from 'react';
import { useCompetitionWorkspace } from '@/features/competition-workspace/CompetitionWorkspaceContext';
import { CompetitionWorkspacePageFrame } from '@/features/competition-workspace/CompetitionWorkspacePageFrame';
import { ManagementTable } from '@/features/management/ManagementTable';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { useResponsive } from '@/hooks/useResponsive';
import { listCompetitionWorkspaceAudit } from '@/services/competition/api';
import type { CompetitionAuditRecord } from '@/services/competition/types';

const auditModuleValueEnum = {
  BASIC: { text: '\u57fa\u7840\u4fe1\u606f' },
  PUBLISH: { text: '\u53d1\u5e03' },
  'STAGE-MATERIALS': { text: '\u9636\u6bb5\u6750\u6599' },
  FILES: { text: '\u6587\u4ef6' },
  FIELDS: { text: '\u5b57\u6bb5' },
} as const;

const auditActionValueEnum = {
  CREATE_COMPETITION: { text: '\u521b\u5efa\u8d5b\u4e8b' },
  UPDATE_COMPETITION: { text: '\u66f4\u65b0\u8d5b\u4e8b' },
  PUBLISH_SETTINGS: { text: '\u53d1\u5e03\u8bbe\u7f6e' },
  SAVE_SETTINGS: { text: '\u4fdd\u5b58\u8bbe\u7f6e' },
  UPDATE_DRAFT: { text: '\u66f4\u65b0\u8349\u7a3f' },
  CREATE_DRAFT: { text: '\u521b\u5efa\u8349\u7a3f' },
} as const;

const firstAuditFilterValue = (value: unknown) => {
  if (Array.isArray(value)) {
    return value.length > 0 ? String(value[0]) : undefined;
  }
  return typeof value === 'string' && value.trim() ? value : undefined;
};

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
      valueEnum: auditModuleValueEnum,
      width: 160,
      render: (value) => <Tag color="blue">{value || '-'}</Tag>,
    },
    {
      title: '动作',
      dataIndex: 'action',
      valueEnum: auditActionValueEnum,
      width: 160,
      render: (value) => value || '-',
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

  const tableRequest = useMemo(
    () => buildTableRequest<CompetitionAuditRecord>(async (params) => {
      if (!competitionUuid) {
        return { records: [], total: 0 };
      }
      return listCompetitionWorkspaceAudit(competitionUuid, {
        module: firstAuditFilterValue(params.module),
        action: firstAuditFilterValue(params.action),
        pageNo: params.pageNo,
        pageSize: params.pageSize,
      });
    }),
    [competitionUuid],
  );

  if (!competitionUuid || !workspace) return null;

  return (
    <CompetitionWorkspacePageFrame
      embeddedInWorkspace
      title={<span><AuditOutlined aria-hidden /> 赛事审计</span>}
      showWorkspaceHeader
      workspaceVariant="table"
    >
      <ManagementTable<CompetitionAuditRecord>
        rowKey="id"
        columns={columns}
        isMobile={responsive.isMobile}
        search={{
          labelWidth: 'auto',
          span: responsive.isMobile ? 24 : 8,
          defaultCollapsed: false,
        }}
        scroll={{
          x: '100%',
          y: responsive.isMobile
            ? 'max(260px, calc(100dvh - 640px))'
            : 'max(280px, calc(100dvh - 590px))',
        }}
        request={tableRequest}
        pagination={{ pageSize: 20, showSizeChanger: true }}
      />
    </CompetitionWorkspacePageFrame>
  );
};

export default AuditPage;
