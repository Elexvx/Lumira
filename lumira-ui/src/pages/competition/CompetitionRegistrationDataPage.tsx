import { DownloadOutlined, EyeOutlined, FileZipOutlined, TeamOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { Alert, Button, Card, Descriptions, Empty, Space, Spin, Table, Tag, Typography } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { StandardDrawer } from '@/features/management/StandardDrawer';
import { useResponsive } from '@/hooks/useResponsive';
import { message } from '@/theme/antdFeedbackBridge';
import { requestFile } from '@/services/common/request';
import {
  getRegistration,
  listCompetitionStages,
  listCompetitions,
  listRegistrationMaterials,
  listRegistrations,
  getCompetitionRegistrationExportTask,
  startCompetitionRegistrationExport,
  startCompetitionRegistrationMaterialPackage,
} from '@/services/competition/api';
import type {
  CompetitionMaterialSubmissionRecord,
  CompetitionMaterialValueRecord,
  CompetitionRecord,
  CompetitionRegistrationRecord,
  CompetitionStageRecord,
} from '@/services/competition/types';
import { showErrorMessage } from '@/utils/errorMessage';
import './CompetitionRegistrationDataPage.css';

type RegistrationQuery = {
  competitionId?: number;
  status?: string;
  keyword?: string;
};

type JsonRecord = Record<string, unknown>;

const statusConfig: Record<string, { color: string; text: string }> = {
  DRAFT: { color: 'default', text: '草稿' },
  PENDING_PAYMENT: { color: 'orange', text: '待支付' },
  PAID: { color: 'blue', text: '已支付' },
  CONFIRMED: { color: 'green', text: '已确认' },
  CANCELLED: { color: 'red', text: '已取消' },
};

const parseJson = <T,>(value: string | null | undefined, fallback: T): T => {
  if (!value?.trim()) return fallback;
  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
};

const valueText = (value: unknown) => {
  if (value == null || value === '') return '-';
  if (typeof value === 'boolean') return value ? '是' : '否';
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  return JSON.stringify(value);
};

const SnapshotCard = ({ title, values }: { title: string; values: JsonRecord }) => {
  const entries = Object.entries(values).filter(([key]) => key !== 'extraValues');
  const extras = values.extraValues && typeof values.extraValues === 'object' && !Array.isArray(values.extraValues)
    ? Object.entries(values.extraValues as JsonRecord)
    : [];
  const items = [...entries, ...extras.map(([key, value]) => [`自定义-${key}`, value] as const)];
  return (
    <Card size="small" title={title}>
      {items.length ? (
        <Descriptions
          bordered
          size="small"
          column={{ xs: 1, sm: 2, md: 2 }}
          items={items.map(([key, value]) => ({
            key,
            label: key,
            children: <Typography.Text copyable={typeof value === 'string'}>{valueText(value)}</Typography.Text>,
          }))}
        />
      ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />}
    </Card>
  );
};

const downloadBlob = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
};

const CompetitionRegistrationDataPage = () => {
  const actionRef = useRef<ActionType>(undefined);
  const access = useAccess();
  const responsive = useResponsive();
  const [competitions, setCompetitions] = useState<CompetitionRecord[]>([]);
  const [lastQuery, setLastQuery] = useState<RegistrationQuery>({});
  const [selectedRows, setSelectedRows] = useState<CompetitionRegistrationRecord[]>([]);
  const [exporting, setExporting] = useState(false);
  const [packaging, setPackaging] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<CompetitionRegistrationRecord>();
  const [materials, setMaterials] = useState<CompetitionMaterialSubmissionRecord[]>([]);
  const [stages, setStages] = useState<CompetitionStageRecord[]>([]);
  const [downloadingFileId, setDownloadingFileId] = useState<number>();

  useEffect(() => {
    let active = true;
    void listCompetitions({ pageNo: 1, pageSize: 100 })
      .then((response) => {
        if (active) setCompetitions(response.records || []);
      })
      .catch((error) => showErrorMessage(error, '赛事列表加载失败'));
    return () => { active = false; };
  }, []);

  const competitionTitleById = useMemo(
    () => new Map(competitions.map((competition) => [
      competition.id,
      competition.title || competition.shortName || competition.code || `赛事 ${competition.id}`,
    ])),
    [competitions],
  );
  const stageNameById = useMemo(
    () => new Map(stages.map((stage) => [stage.id, stage.stageName || stage.stageCode])),
    [stages],
  );

  const openDetail = async (record: CompetitionRegistrationRecord) => {
    setDetailOpen(true);
    setDetailLoading(true);
    setDetail(undefined);
    setMaterials([]);
    setStages([]);
    try {
      const [registration, materialRecords, stageRecords] = await Promise.all([
        getRegistration(record.id),
        listRegistrationMaterials(record.id),
        listCompetitionStages(record.competitionId),
      ]);
      setDetail(registration);
      setMaterials(materialRecords || []);
      setStages(stageRecords || []);
    } catch (error) {
      showErrorMessage(error, '报名团队资料加载失败');
    } finally {
      setDetailLoading(false);
    }
  };

  const exportRows = async (records?: CompetitionRegistrationRecord[]) => {
    setExporting(true);
    try {
      const selectedCompetitionIds = Array.from(
        new Set((records || []).map((record) => record.competitionId)),
      );
      const competitionId = selectedCompetitionIds[0] || lastQuery.competitionId;
      if (!competitionId) {
        message.warning('请先选择比赛数据表');
        return;
      }
      if (selectedCompetitionIds.length > 1) {
        message.warning('一次只能导出同一场比赛的数据');
        return;
      }
      const started = await startCompetitionRegistrationExport({
        competitionId,
        status: records?.length ? undefined : lastQuery.status,
        keyword: records?.length ? undefined : lastQuery.keyword,
        registrationIds: records?.length ? records.map((record) => record.id) : undefined,
      });
      message.success(`已创建导出任务，共 ${started.totalCount} 个团队`);

      let finished = false;
      for (let attempt = 0; attempt < 120; attempt += 1) {
        const task = await getCompetitionRegistrationExportTask(started.taskId);
        if (task.status === 'FAILED') {
          throw new Error(task.errorMessage || '服务端导出任务失败');
        }
        if (task.status === 'SUCCESS') {
          if (!task.downloadUrl) throw new Error('导出文件下载地址缺失');
          const downloadPath = task.downloadUrl.replace(/^\/api(?=\/)/, '');
          const blob = await requestFile(downloadPath, { method: 'GET', silent: true });
          downloadBlob(blob, task.fileName || started.fileName);
          message.success('报名团队资料已生成并开始下载');
          finished = true;
          break;
        }
        await new Promise((resolve) => window.setTimeout(resolve, 1_500));
      }
      if (!finished) {
        message.info('导出仍在后台处理，可稍后在下载中心获取');
      }
    } catch (error) {
      showErrorMessage(error, '报名团队资料导出失败');
    } finally {
      setExporting(false);
    }
  };

  const packageMaterials = async (records?: CompetitionRegistrationRecord[]) => {
    setPackaging(true);
    try {
      const selectedCompetitionIds = Array.from(
        new Set((records || []).map((record) => record.competitionId)),
      );
      const competitionId = selectedCompetitionIds[0] || lastQuery.competitionId;
      if (!competitionId) {
        message.warning('请先选择比赛数据集');
        return;
      }
      if (selectedCompetitionIds.length > 1) {
        message.warning('一次只能打包同一场比赛的材料');
        return;
      }
      const started = await startCompetitionRegistrationMaterialPackage({
        competitionId,
        status: records?.length ? undefined : lastQuery.status,
        keyword: records?.length ? undefined : lastQuery.keyword,
        registrationIds: records?.length ? records.map((record) => record.id) : undefined,
      });
      message.success(`已创建材料打包任务，共 ${started.totalCount} 个团队`);

      let finished = false;
      for (let attempt = 0; attempt < 120; attempt += 1) {
        const task = await getCompetitionRegistrationExportTask(started.taskId);
        if (task.status === 'FAILED') {
          throw new Error(task.errorMessage || '服务端材料打包任务失败');
        }
        if (task.status === 'SUCCESS') {
          if (!task.downloadUrl) throw new Error('材料包下载地址缺失');
          const downloadPath = task.downloadUrl.replace(/^\/api(?=\/)/, '');
          const blob = await requestFile(downloadPath, { method: 'GET', silent: true });
          downloadBlob(blob, task.fileName || started.fileName);
          message.success('报名材料包已生成并开始下载');
          finished = true;
          break;
        }
        await new Promise((resolve) => window.setTimeout(resolve, 1_500));
      }
      if (!finished) {
        message.info('材料仍在后台打包，可稍后在下载中心获取');
      }
    } catch (error) {
      showErrorMessage(error, '报名材料打包失败');
    } finally {
      setPackaging(false);
    }
  };

  const downloadMaterial = async (value: CompetitionMaterialValueRecord) => {
    if (!value.fileId || !detail?.id) return;
    setDownloadingFileId(value.fileId);
    try {
      const blob = await requestFile(
        `/v2/aiadc/registrations/${detail.id}/materials/files/${value.fileId}/download`,
        {
          method: 'GET',
          silent: true,
        },
      );
      downloadBlob(blob, `${value.fieldKey}-${value.fileId}`);
    } catch (error) {
      showErrorMessage(error, '材料下载失败');
    } finally {
      setDownloadingFileId(undefined);
    }
  };

  const columns = useMemo<ProColumns<CompetitionRegistrationRecord>[]>(() => [
    {
      title: '关键词',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '报名编号 / 参赛编号 / 团队 / 项目' },
    },
    {
      title: '赛事',
      dataIndex: 'competitionId',
      valueType: 'select',
      fieldProps: {
        showSearch: true,
        optionFilterProp: 'label',
        placeholder: '请选择要查看的数据表',
        options: competitions.map((competition) => ({
          label: competition.title || competition.shortName || competition.code,
          value: competition.id,
        })),
      },
      formItemProps: {
        rules: [{ required: true, message: '请选择比赛数据表' }],
      },
      minWidth: 220,
      render: (_, record) => competitionTitleById.get(record.competitionId) || `赛事 ${record.competitionId}`,
    },
    {
      title: '报名团队',
      dataIndex: 'teamName',
      search: false,
      minWidth: 190,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.teamName || `团队 ${record.teamId || '-'}`}</Typography.Text>
          <Typography.Text type="secondary">{record.registrationNo}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '项目',
      dataIndex: 'projectTitle',
      search: false,
      minWidth: 180,
      ellipsis: true,
    },
    {
      title: '学生人数',
      dataIndex: 'memberCount',
      search: false,
      width: 100,
      align: 'center',
    },
    {
      title: '材料',
      dataIndex: 'materialFileCount',
      search: false,
      width: 130,
      render: (_, record) => (
        <Space size={4}>
          <Tag color={record.materialSubmissionCount ? 'blue' : 'default'}>
            {record.materialSubmissionCount || 0} 次提交
          </Tag>
          <Tag color={record.materialFileCount ? 'cyan' : 'default'}>
            {record.materialFileCount || 0} 个文件
          </Tag>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(statusConfig).map(([key, value]) => [key, { text: value.text }])),
      width: 110,
      render: (_, record) => {
        const config = statusConfig[record.status] || { color: 'default', text: record.status };
        return <Tag color={config.color}>{config.text}</Tag>;
      },
    },
    {
      title: '参赛编号',
      dataIndex: 'participantNo',
      search: false,
      width: 150,
      render: (_, record) => record.participantNo || '-',
    },
    {
      title: '报名时间',
      dataIndex: 'createdAt',
      search: false,
      width: 180,
      valueType: 'dateTime',
    },
    {
      title: '操作',
      valueType: 'option',
      fixed: 'right',
      width: 120,
      render: (_, record) => (
        <Button type="link" icon={<EyeOutlined />} onClick={() => void openDetail(record)}>
          查看资料
        </Button>
      ),
    },
  ], [competitionTitleById, competitions]);

  const registrationValues = parseJson<JsonRecord>(detail?.registrationSnapshotJson, {});
  const teamValues = parseJson<JsonRecord>(detail?.teamSnapshotJson, {});
  const projectValues = parseJson<JsonRecord>(detail?.projectSnapshotJson, {});
  const memberValues = parseJson<JsonRecord[]>(detail?.memberSnapshotJson, []);
  const tableToolbarActions = access.canExportCompetitionRegistrations ? [
    <Button
      key="export-selected"
      icon={<TeamOutlined />}
      disabled={!selectedRows.length}
      loading={exporting}
      onClick={() => void exportRows(selectedRows)}
    >
      导出所选团队
    </Button>,
    <Button
      key="export-filtered"
      type="primary"
      icon={<FileZipOutlined />}
      loading={exporting}
      onClick={() => void exportRows()}
    >
      导出筛选结果
    </Button>,
    ...(access.canDownloadRegistrationMaterials ? [
      <Button
        key="download-selected-materials"
        icon={<FileZipOutlined />}
        disabled={!selectedRows.length}
        loading={packaging}
        onClick={() => void packageMaterials(selectedRows)}
      >
        下载所选材料包
      </Button>,
      <Button
        key="download-filtered-materials"
        type="primary"
        icon={<FileZipOutlined />}
        loading={packaging}
        onClick={() => void packageMaterials()}
      >
        下载筛选材料包
      </Button>,
    ] : []),
  ] : [];

  return (
    <ManagementPage
      title="报名团队资料"
      breadcrumb={{
        items: [
          { key: 'data-management', title: '数据管理', path: '/data-management' },
          { key: 'competition-registrations', title: '报名团队资料' },
        ],
      }}
    >
      <ManagementPageBody>
        <Alert
          className="competition-registration-data-page__notice"
          type={access.canViewSensitiveCompetitionRegistrations ? 'info' : 'warning'}
          showIcon
          message={access.canViewSensitiveCompetitionRegistrations
            ? '每场比赛使用独立的逻辑报名数据集。服务端 XLSX 会按每位学生一行展开。'
            : '每场比赛使用独立的逻辑报名数据集；当前角色只能查看脱敏资料。'}
          description={access.canExportCompetitionRegistrations && !access.canExportSensitiveCompetitionRegistrations
            ? '导出的姓名、学号/工号会脱敏，完整快照与材料元数据不会写入文件。'
            : undefined}
        />
        <ManagementTable<CompetitionRegistrationRecord>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          isMobile={responsive.isMobile}
          scroll={{ x: 1420 }}
          toolBarRender={() => tableToolbarActions}
          request={async (params) => {
            const query: RegistrationQuery = {
              competitionId: typeof params.competitionId === 'number'
                ? params.competitionId
                : Number(params.competitionId) || undefined,
              status: typeof params.status === 'string' ? params.status : undefined,
              keyword: typeof params.keyword === 'string' ? params.keyword.trim() || undefined : undefined,
            };
            setLastQuery(query);
            if (!query.competitionId) {
              return { data: [], total: 0, success: true };
            }
            const response = await listRegistrations({
              ...query,
              pageNo: params.current,
              pageSize: params.pageSize,
            });
            return { data: response.records, total: response.total, success: true };
          }}
          pagination={{ pageSize: 20, showSizeChanger: true }}
          rowSelection={{
            preserveSelectedRowKeys: true,
            onChange: (_, rows) => setSelectedRows(rows),
          }}
        />
      </ManagementPageBody>

      <StandardDrawer
        title={detail ? `${detail.teamName || '报名团队'} · 完整资料` : '报名团队资料'}
        open={detailOpen}
        destroyOnHidden
        onClose={() => setDetailOpen(false)}
        extra={detail && access.canExportCompetitionRegistrations ? (
          <Button
            icon={<DownloadOutlined />}
            onClick={() => void exportRows([detail])}
          >
            导出该团队
          </Button>
        ) : null}
      >
        <Spin spinning={detailLoading}>
          {detail ? (
            <Space className="competition-registration-data-page__detail" direction="vertical" size={16}>
              <Card size="small" title="报名概览">
                <Descriptions
                  bordered
                  size="small"
                  column={{ xs: 1, sm: 2, md: 3 }}
                  items={[
                    { key: 'competition', label: '赛事', children: competitionTitleById.get(detail.competitionId) || `赛事 ${detail.competitionId}` },
                    { key: 'registrationNo', label: '报名编号', children: detail.registrationNo },
                    { key: 'participantNo', label: '参赛编号', children: detail.participantNo || '-' },
                    { key: 'team', label: '团队', children: detail.teamName || valueText(teamValues.teamName) },
                    { key: 'project', label: '项目', children: detail.projectTitle || valueText(projectValues.title) },
                    { key: 'members', label: '学生人数', children: detail.memberCount },
                    { key: 'status', label: '状态', children: statusConfig[detail.status]?.text || detail.status },
                    { key: 'createdAt', label: '报名时间', children: detail.createdAt || '-' },
                    { key: 'updatedAt', label: '更新时间', children: detail.updatedAt || '-' },
                  ]}
                />
              </Card>
              <SnapshotCard title="报名信息" values={registrationValues} />
              <SnapshotCard title="团队信息" values={teamValues} />
              <SnapshotCard title="项目信息" values={projectValues} />
              <Card size="small" title={`学生成员（${memberValues.length}）`}>
                <Table<JsonRecord>
                  rowKey={(_, index) => String(index)}
                  size="small"
                  pagination={false}
                  scroll={{ x: 900 }}
                  dataSource={memberValues}
                  columns={[
                    { title: '姓名', dataIndex: 'memberName', render: (value) => valueText(value) },
                    { title: '角色', dataIndex: 'role', render: (value) => valueText(value) },
                    { title: '学号/工号', dataIndex: 'employeeNo', render: (value) => valueText(value) },
                    { title: '院系/部门', dataIndex: 'departmentName', render: (value) => valueText(value) },
                    {
                      title: '自定义资料',
                      dataIndex: 'extraValues',
                      render: (value) => valueText(value),
                    },
                    { title: '备注', dataIndex: 'remark', render: (value) => valueText(value) },
                  ]}
                />
              </Card>
              <Card size="small" title={`阶段材料（${materials.length} 次提交）`}>
                {materials.length ? materials.map((submission) => (
                  <Card
                    className="competition-registration-data-page__submission"
                    key={submission.id}
                    size="small"
                    type="inner"
                    title={stageNameById.get(submission.stageId) || `阶段 ${submission.stageId}`}
                    extra={<Tag color="green">{submission.status}</Tag>}
                  >
                    <Table<CompetitionMaterialValueRecord>
                      rowKey="id"
                      size="small"
                      pagination={false}
                      dataSource={submission.values || []}
                      columns={[
                        { title: '材料字段', dataIndex: 'fieldKey', width: 220 },
                        { title: '类型', dataIndex: 'fieldType', width: 110 },
                        {
                          title: '内容',
                          render: (_, value) => value.fileId
                            ? `文件 #${value.fileId}`
                            : valueText(value.textValue || parseJson(value.jsonValue, value.jsonValue || '')),
                        },
                        {
                          title: '操作',
                          width: 100,
                          render: (_, value) => value.fileId && access.canDownloadRegistrationMaterials ? (
                            <Button
                              type="link"
                              icon={<DownloadOutlined />}
                              loading={downloadingFileId === value.fileId}
                              onClick={() => void downloadMaterial(value)}
                            >
                              下载
                            </Button>
                          ) : null,
                        },
                      ]}
                    />
                  </Card>
                )) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂未提交阶段材料" />}
              </Card>
            </Space>
          ) : detailLoading ? null : <Empty description="未能加载报名资料" />}
        </Spin>
      </StandardDrawer>
    </ManagementPage>
  );
};

export default CompetitionRegistrationDataPage;
