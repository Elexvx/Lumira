import {
  DownloadOutlined,
  EyeOutlined,
  FileExcelOutlined,
  FileZipOutlined,
} from "@ant-design/icons";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { useAccess } from "@umijs/max";
import {
  Button,
  Card,
  Descriptions,
  Empty,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from "antd";
import {
  type Key,
  useCallback,
  useMemo,
  useRef,
  useState,
} from "react";
import { ManagementTable } from "@/features/management/ManagementTable";
import { StandardDrawer } from "@/features/management/StandardDrawer";
import { useResponsive } from "@/hooks/useResponsive";
import { message } from "@/theme/antdFeedbackBridge";
import { requestFile } from "@/services/common/request";
import {
  getCompetitionWorkspaceExportTask,
  getCompetitionWorkspaceRegistration,
  listCompetitionWorkspaceRegistrationMaterials,
  listCompetitionWorkspaceRegistrations,
  listCompetitionWorkspaceStages,
  startCompetitionWorkspaceMaterialPackage,
  startCompetitionWorkspaceRegistrationExport,
} from "@/services/competition/api";
import type {
  CompetitionMaterialSubmissionRecord,
  CompetitionMaterialValueRecord,
  CompetitionRegistrationRecord,
  CompetitionStageRecord,
  CompetitionWorkspaceExportRequest,
} from "@/services/competition/types";
import {
  getRegistrationStatusLabel,
  registrationStatusValueEnum,
} from "@/pages/competition/utils/registrationStatus";
import {
  buildRegistrationQuerySignature,
  MAX_SELECTED_REGISTRATION_COUNT,
  resolveRegistrationExportScope,
  type RegistrationExportQuery,
} from "@/pages/competition/registrationExportScope";
import { showErrorMessage } from "@/utils/errorMessage";
import { useCompetitionWorkspace } from "@/features/competition-workspace/CompetitionWorkspaceContext";
import { CompetitionRegistrationDataPageFrame } from "./CompetitionRegistrationDataPageFrame";
import "./CompetitionRegistrationDataPage.css";

type JsonRecord = Record<string, unknown>;

const statusColor: Record<string, string> = {
  DRAFT: "default",
  PENDING_PAYMENT: "orange",
  PAID: "blue",
  CONFIRMED: "green",
  CANCELLED: "red",
};

const dataPageRegistrationStatusValueEnum = Object.fromEntries(
  Object.keys(statusColor).map((status) => [
    status,
    registrationStatusValueEnum[status],
  ]),
);

const parseJson = <T,>(value: string | null | undefined, fallback: T): T => {
  if (!value?.trim()) return fallback;
  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
};

const valueText = (value: unknown) => {
  if (value == null || value === "") return "-";
  if (typeof value === "boolean") return value ? "是" : "否";
  if (typeof value === "string" || typeof value === "number")
    return String(value);
  return JSON.stringify(value);
};

const SnapshotCard = ({
  title,
  values,
}: {
  title: string;
  values: JsonRecord;
}) => {
  const entries = Object.entries(values).filter(
    ([key]) => key !== "extraValues",
  );
  const extras =
    values.extraValues &&
    typeof values.extraValues === "object" &&
    !Array.isArray(values.extraValues)
      ? Object.entries(values.extraValues as JsonRecord)
      : [];
  const items = [
    ...entries,
    ...extras.map(([key, value]) => [`自定义-${key}`, value] as const),
  ];
  return (
    <Card size="small" title={title}>
      {items.length ? (
        <Descriptions
          bordered
          size="small"
          column={1}
          items={items.map(([key, value]) => ({
            key,
            label: key,
            children: (
              <Typography.Text copyable={typeof value === "string"}>
                {valueText(value)}
              </Typography.Text>
            ),
          }))}
        />
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />
      )}
    </Card>
  );
};

const downloadBlob = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
};

const CompetitionRegistrationDataPage = () => {
  const actionRef = useRef<ActionType>(undefined);
  const lastQuerySignatureRef = useRef<string | undefined>(undefined);
  const access = useAccess();
  const responsive = useResponsive();
  const workspaceContext = useCompetitionWorkspace();
  const workspaceUuid = workspaceContext.competitionUuid!;
  const workspaceTitle = workspaceContext.workspace?.title;
  const [lastQuery, setLastQuery] = useState<RegistrationExportQuery>({});
  const [resultTotal, setResultTotal] = useState(0);
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  const [exporting, setExporting] = useState(false);
  const [packaging, setPackaging] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<CompetitionRegistrationRecord>();
  const [materials, setMaterials] = useState<
    CompetitionMaterialSubmissionRecord[]
  >([]);
  const [stages, setStages] = useState<CompetitionStageRecord[]>([]);
  const [downloadingFileId, setDownloadingFileId] = useState<number>();

  const clearSelection = useCallback(() => setSelectedRowKeys([]), []);
  const selectedRegistrationIds = useMemo(
    () =>
      selectedRowKeys
        .map((key) => (typeof key === "number" ? key : Number(key)))
        .filter((key) => Number.isInteger(key) && key > 0),
    [selectedRowKeys],
  );

  const stageNameById = useMemo(
    () =>
      new Map(
        stages.map((stage) => [stage.id, stage.stageName || stage.stageCode]),
      ),
    [stages],
  );

  const openDetail = useCallback(
    async (record: CompetitionRegistrationRecord) => {
      setDetailOpen(true);
      setDetailLoading(true);
      setDetail(undefined);
      setMaterials([]);
      setStages([]);
      try {
        const [registration, materialRecords, stageRecords] = await Promise.all([
          getCompetitionWorkspaceRegistration(workspaceUuid, record.id),
          listCompetitionWorkspaceRegistrationMaterials(workspaceUuid, record.id),
          listCompetitionWorkspaceStages(workspaceUuid),
        ]);
        setDetail(registration);
        setMaterials(materialRecords || []);
        setStages(stageRecords || []);
      } catch (error) {
        showErrorMessage(error, "报名与材料加载失败");
      } finally {
        setDetailLoading(false);
      }
    },
    [workspaceUuid],
  );

  const exportRows = async (registrationIds: number[] = []) => {
    setExporting(true);
    try {
      const exportRequest: CompetitionWorkspaceExportRequest = {
        status: lastQuery.status,
        keyword: lastQuery.keyword,
        registrationIds: registrationIds.length ? registrationIds : undefined,
      };
      const started = await startCompetitionWorkspaceRegistrationExport(
        workspaceUuid,
        exportRequest,
      );
      message.success(`已创建导出任务，共 ${started.totalCount} 个团队`);

      let finished = false;
      for (let attempt = 0; attempt < 120; attempt += 1) {
        const task = await getCompetitionWorkspaceExportTask(
          workspaceUuid,
          started.taskId,
        );
        if (task.status === "FAILED") {
          const reason = task.errorMessage?.trim() || "服务端未提供失败原因";
          throw new Error(`导出任务失败：${reason}`);
        }
        if (task.status === "SUCCESS") {
          if (!task.downloadUrl) throw new Error("导出文件下载地址缺失");
          const downloadPath = task.downloadUrl.replace(/^\/api(?=\/)/, "");
          const blob = await requestFile(downloadPath, {
            method: "GET",
            silent: true,
          });
          downloadBlob(blob, task.fileName || started.fileName);
          message.success("报名记录 Excel 已生成并开始下载");
          finished = true;
          break;
        }
        await new Promise((resolve) => window.setTimeout(resolve, 1_500));
      }
      if (!finished) {
        message.info("导出仍在后台处理，可稍后在下载中心获取");
      }
    } catch (error) {
      showErrorMessage(error, "报名与材料导出失败");
    } finally {
      setExporting(false);
    }
  };

  const packageMaterials = async (registrationIds: number[] = []) => {
    setPackaging(true);
    try {
      const exportRequest: CompetitionWorkspaceExportRequest = {
        status: lastQuery.status,
        keyword: lastQuery.keyword,
        registrationIds: registrationIds.length ? registrationIds : undefined,
      };
      const started = await startCompetitionWorkspaceMaterialPackage(
        workspaceUuid,
        exportRequest,
      );
      message.success(
        `已创建完整材料导出任务，共 ${started.totalCount} 个团队`,
      );

      let finished = false;
      for (let attempt = 0; attempt < 120; attempt += 1) {
        const task = await getCompetitionWorkspaceExportTask(
          workspaceUuid,
          started.taskId,
        );
        if (task.status === "FAILED") {
          const reason = task.errorMessage?.trim() || "服务端未提供失败原因";
          throw new Error(`材料打包任务失败：${reason}`);
        }
        if (task.status === "SUCCESS") {
          if (!task.downloadUrl) throw new Error("材料包下载地址缺失");
          const downloadPath = task.downloadUrl.replace(/^\/api(?=\/)/, "");
          const blob = await requestFile(downloadPath, {
            method: "GET",
            silent: true,
          });
          downloadBlob(blob, task.fileName || started.fileName);
          message.success("完整材料包（含报名记录 Excel）已生成并开始下载");
          finished = true;
          break;
        }
        await new Promise((resolve) => window.setTimeout(resolve, 1_500));
      }
      if (!finished) {
        message.info("材料仍在后台打包，可稍后在下载中心获取");
      }
    } catch (error) {
      showErrorMessage(error, "报名材料打包失败");
    } finally {
      setPackaging(false);
    }
  };

  const downloadMaterial = async (value: CompetitionMaterialValueRecord) => {
    if (!value.fileId || !detail?.id) return;
    setDownloadingFileId(value.fileId);
    try {
      const blob = await requestFile(
        `/v2/aiadc/competitions/${encodeURIComponent(workspaceUuid)}/registrations/${detail.id}/materials/files/${value.fileId}/download`,
        {
          method: "GET",
          silent: true,
        },
      );
      downloadBlob(blob, `${value.fieldKey}-${value.fileId}`);
    } catch (error) {
      showErrorMessage(error, "材料下载失败");
    } finally {
      setDownloadingFileId(undefined);
    }
  };

  const columns = useMemo<ProColumns<CompetitionRegistrationRecord>[]>(
    () => [
      {
        title: "关键词",
        dataIndex: "keyword",
        hideInTable: true,
        fieldProps: { placeholder: "报名编号 / 参赛编号 / 团队 / 项目" },
      },
      {
        title: "报名团队",
        dataIndex: "teamName",
        search: false,
        minWidth: 210,
        render: (_, record) => (
          <Space
            className="competition-registration-data-page__team-cell"
            direction="vertical"
            size={0}
          >
            <Typography.Text
              strong
              ellipsis={{ tooltip: record.teamName || undefined }}
            >
              {record.teamName || `团队 ${record.teamId || "-"}`}
            </Typography.Text>
            <Typography.Text
              type="secondary"
              ellipsis={{ tooltip: record.registrationNo }}
            >
              {record.registrationNo}
            </Typography.Text>
          </Space>
        ),
      },
      {
        title: "项目",
        dataIndex: "projectTitle",
        search: false,
        minWidth: 180,
        ellipsis: true,
        responsive: ["sm", "md", "lg", "xl", "xxl"],
      },
      {
        title: "学生人数",
        dataIndex: "memberCount",
        search: false,
        width: 88,
        align: "center",
        responsive: ["md", "lg", "xl", "xxl"],
      },
      {
        title: "材料",
        dataIndex: "materialFileCount",
        search: false,
        width: 170,
        responsive: ["lg", "xl", "xxl"],
        render: (_, record) => (
          <Space size={[4, 4]} wrap>
            <Tag color={record.materialSubmissionCount ? "blue" : "default"}>
              {record.materialSubmissionCount || 0} 次提交
            </Tag>
            <Tag color={record.materialFileCount ? "cyan" : "default"}>
              {record.materialFileCount || 0} 个文件
            </Tag>
          </Space>
        ),
      },
      {
        title: "状态",
        dataIndex: "status",
        valueType: "select",
        valueEnum: dataPageRegistrationStatusValueEnum,
        width: 96,
        render: (_, record) => (
          <Tag color={statusColor[record.status] || "default"}>
            {getRegistrationStatusLabel(record.status, "DRAFT")}
          </Tag>
        ),
      },
      {
        title: "参赛编号",
        dataIndex: "participantNo",
        search: false,
        width: 150,
        responsive: ["xl", "xxl"],
        ellipsis: true,
        render: (_, record) => record.participantNo || "-",
      },
      {
        title: "报名时间",
        dataIndex: "createdAt",
        search: false,
        width: 180,
        valueType: "dateTime",
        responsive: ["xxl"],
      },
      {
        title: "操作",
        valueType: "option",
        fixed: responsive.isDesktop ? "right" : undefined,
        width: 104,
        render: (_, record) => (
          <Button
            type="link"
            icon={<EyeOutlined />}
            onClick={() => void openDetail(record)}
          >
            查看资料
          </Button>
        ),
      },
    ],
    [
      openDetail,
      responsive.isDesktop,
    ],
  );

  const registrationValues = parseJson<JsonRecord>(
    detail?.registrationSnapshotJson,
    {},
  );
  const teamValues = parseJson<JsonRecord>(detail?.teamSnapshotJson, {});
  const projectValues = parseJson<JsonRecord>(detail?.projectSnapshotJson, {});
  const memberValues = parseJson<JsonRecord[]>(detail?.memberSnapshotJson, []);
  const exportScope = resolveRegistrationExportScope({
    hasCompetition: true,
    filteredCount: resultTotal,
    selectedCount: selectedRegistrationIds.length,
  });
  const scopedRegistrationIds =
    exportScope.mode === "selected" ? selectedRegistrationIds : [];
  const tableToolbarActions = access.canExportCompetitionRegistrations
    ? [
        <Button
          key="export-registrations"
          type="primary"
          icon={<FileExcelOutlined aria-hidden />}
          disabled={exportScope.disabled || packaging}
          loading={exporting}
          title="仅下载报名记录 Excel，不包含资料文件"
          onClick={() => void exportRows(scopedRegistrationIds)}
        >
          {exportScope.exportLabel}
        </Button>,
        ...(access.canDownloadRegistrationMaterials
          ? [
              <Button
                key="download-materials"
                icon={<FileZipOutlined aria-hidden />}
                disabled={exportScope.disabled || exporting}
                loading={packaging}
                title="下载 ZIP：包含报名记录 Excel 和按编号分组的资料文件夹"
                onClick={() => void packageMaterials(scopedRegistrationIds)}
              >
                {exportScope.materialPackageLabel}
              </Button>,
            ]
          : []),
      ]
    : [];

  const tableBody = (
    <CompetitionRegistrationDataPageFrame>
      <ManagementTable<CompetitionRegistrationRecord>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        autoContentWidth
        containerResponsive
        isMobile={responsive.isMobile}
        scroll={{ x: "max-content" }}
        tableLayout="fixed"
        toolBarRender={() => tableToolbarActions}
        request={async (params) => {
          const query: RegistrationExportQuery = {
            status:
              typeof params.status === "string" ? params.status : undefined,
            keyword:
              typeof params.keyword === "string"
                ? params.keyword.trim() || undefined
                : undefined,
          };
          const querySignature = buildRegistrationQuerySignature(query);
          if (lastQuerySignatureRef.current !== querySignature) {
            lastQuerySignatureRef.current = querySignature;
            clearSelection();
            setResultTotal(0);
          }
          setLastQuery(query);
          setResultTotal(0);
          const response = await listCompetitionWorkspaceRegistrations(
            workspaceUuid,
            {
              status: query.status,
              keyword: query.keyword,
              includeSnapshots: true,
              pageNo: params.current,
              pageSize: params.pageSize,
            },
          );
          setResultTotal(response.total || 0);
          return {
            data: response.records,
            total: response.total,
            success: true,
          };
        }}
        pagination={{ pageSize: 20, showSizeChanger: true }}
        rowSelection={{
          selectedRowKeys,
          preserveSelectedRowKeys: true,
          onChange: (nextSelectedRowKeys) => {
            if (nextSelectedRowKeys.length > MAX_SELECTED_REGISTRATION_COUNT) {
              message.warning(
                `一次最多选择 ${MAX_SELECTED_REGISTRATION_COUNT} 个团队`,
              );
              return;
            }
            setSelectedRowKeys(nextSelectedRowKeys);
          },
        }}
        tableAlertRender={({ selectedRowKeys: tableSelectedRowKeys }) => (
          <Typography.Text role="status" aria-live="polite">
            已选择 {tableSelectedRowKeys.length} 个团队；报名记录导出为
            Excel，完整材料导出为 Excel 加编号资料文件夹。
          </Typography.Text>
        )}
        tableAlertOptionRender={() => (
          <Button type="link" onClick={clearSelection}>
            取消选择
          </Button>
        )}
      />
    </CompetitionRegistrationDataPageFrame>
  );

  const detailDrawer = (
    <StandardDrawer
      title={
        detail ? `${detail.teamName || "报名团队"} · 完整资料` : "报名与材料"
      }
      open={detailOpen}
      destroyOnHidden
      onClose={() => setDetailOpen(false)}
      extra={
        detail && access.canExportCompetitionRegistrations ? (
          <Space wrap>
            <Button
              icon={<FileExcelOutlined />}
              disabled={packaging}
              loading={exporting}
              title="仅下载该条报名记录 Excel，不包含资料文件"
              onClick={() => void exportRows([detail.id])}
            >
              仅导出报名记录
            </Button>
            {access.canDownloadRegistrationMaterials ? (
              <Button
                type="primary"
                icon={<FileZipOutlined />}
                disabled={exporting}
                loading={packaging}
                title="下载 ZIP：包含该条报名记录 Excel 和报名资料"
                onClick={() =>
                  void packageMaterials([detail.id])
                }
              >
                导出完整材料
              </Button>
            ) : null}
          </Space>
        ) : null
      }
    >
      <Spin spinning={detailLoading}>
        {detail ? (
          <Space
            className="competition-registration-data-page__detail"
            direction="vertical"
            size={16}
          >
            <Card size="small" title="报名概览">
              <Descriptions
                bordered
                size="small"
                column={1}
                items={[
                  {
                    key: "competition",
                    label: "赛事",
                    children: workspaceTitle || "当前赛事",
                  },
                  {
                    key: "registrationNo",
                    label: "报名编号",
                    children: detail.registrationNo,
                  },
                  {
                    key: "participantNo",
                    label: "参赛编号",
                    children: detail.participantNo || "-",
                  },
                  {
                    key: "team",
                    label: "团队",
                    children: detail.teamName || valueText(teamValues.teamName),
                  },
                  {
                    key: "project",
                    label: "项目",
                    children:
                      detail.projectTitle || valueText(projectValues.title),
                  },
                  {
                    key: "members",
                    label: "学生人数",
                    children: detail.memberCount,
                  },
                  {
                    key: "status",
                    label: "状态",
                    children: getRegistrationStatusLabel(
                      detail.status,
                      "DRAFT",
                    ),
                  },
                  {
                    key: "createdAt",
                    label: "报名时间",
                    children: detail.createdAt || "-",
                  },
                  {
                    key: "updatedAt",
                    label: "更新时间",
                    children: detail.updatedAt || "-",
                  },
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
                  {
                    title: "姓名",
                    dataIndex: "memberName",
                    render: (value) => valueText(value),
                  },
                  {
                    title: "角色",
                    dataIndex: "role",
                    render: (value) => valueText(value),
                  },
                  {
                    title: "学号/工号",
                    dataIndex: "employeeNo",
                    render: (value) => valueText(value),
                  },
                  {
                    title: "院系/部门",
                    dataIndex: "departmentName",
                    render: (value) => valueText(value),
                  },
                  {
                    title: "自定义资料",
                    dataIndex: "extraValues",
                    render: (value) => valueText(value),
                  },
                  {
                    title: "备注",
                    dataIndex: "remark",
                    render: (value) => valueText(value),
                  },
                ]}
              />
            </Card>
            <Card size="small" title={`阶段材料（${materials.length} 次提交）`}>
              {materials.length ? (
                materials.map((submission) => (
                  <Card
                    className="competition-registration-data-page__submission"
                    key={submission.id}
                    size="small"
                    type="inner"
                    title={
                      stageNameById.get(submission.stageId) ||
                      `阶段 ${submission.stageId}`
                    }
                    extra={<Tag color="green">{submission.status}</Tag>}
                  >
                    <Table<CompetitionMaterialValueRecord>
                      rowKey="id"
                      size="small"
                      pagination={false}
                      dataSource={submission.values || []}
                      columns={[
                        {
                          title: "材料字段",
                          dataIndex: "fieldKey",
                          width: 220,
                        },
                        { title: "类型", dataIndex: "fieldType", width: 110 },
                        {
                          title: "内容",
                          render: (_, value) =>
                            value.fileId
                              ? `文件 #${value.fileId}`
                              : valueText(
                                  value.textValue ||
                                    parseJson(
                                      value.jsonValue,
                                      value.jsonValue || "",
                                    ),
                                ),
                        },
                        {
                          title: "操作",
                          width: 100,
                          render: (_, value) =>
                            value.fileId &&
                            access.canDownloadRegistrationMaterials ? (
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
                ))
              ) : (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="暂未提交阶段材料"
                />
              )}
            </Card>
          </Space>
        ) : detailLoading ? null : (
          <Empty description="未能加载报名资料" />
        )}
      </Spin>
    </StandardDrawer>
  );

  return (
    <>
      {tableBody}
      {detailDrawer}
    </>
  );
};

export default CompetitionRegistrationDataPage;
