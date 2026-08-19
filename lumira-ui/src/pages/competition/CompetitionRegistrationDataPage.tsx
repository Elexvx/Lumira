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
  Tag,
  Typography,
} from "antd";
import type { TableColumnsType } from "antd";
import {
  type Key,
  type ReactNode,
  useCallback,
  useMemo,
  useRef,
  useState,
} from "react";
import { ManagementTable } from "@/features/management/ManagementTable";
import { StandardDrawer } from "@/features/management/StandardDrawer";
import { DataTable } from "@/features/table/DataTable";
import { buildTableRequest } from "@/features/table/proTableRequest";
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
import { saveBlobAsFile } from "@/utils/download";
import { showErrorMessage } from "@/utils/errorMessage";
import { useCompetitionWorkspace } from "@/features/competition-workspace/CompetitionWorkspaceContext";
import { CompetitionRegistrationDataPageFrame } from "./CompetitionRegistrationDataPageFrame";
import { RecentRegistrationExportDownloadButton } from "./RecentRegistrationExportDownloadButton";
import {
  createReadyRegistrationExportDownload,
  downloadReadyRegistrationExport,
  type ReadyRegistrationExportDownload,
} from "./registrationExportDownload";
import { splitRegistrationMemberSnapshots } from "./registrationMemberSnapshots";
import "./CompetitionRegistrationDataPage.css";

type JsonRecord = Record<string, unknown>;

type SnapshotFieldDefinition = {
  scope: string;
  itemKey: string;
  title: string;
  groupLabel?: string;
};

const SNAPSHOT_FIELD_LABELS: Record<string, string> = {
  teamId: "团队 ID",
  teamName: "团队名称",
  title: "项目名称",
  category: "项目类别",
  intellectualProperties: "知识产权信息",
  intellectualPropertyType: "知识产权类型",
  intellectualPropertyName: "知识产权名称",
  rightsHolder: "权利人",
  distributionRegions: "分布区域",
};

const isJsonRecord = (value: unknown): value is JsonRecord =>
  Boolean(value) && typeof value === "object" && !Array.isArray(value);

const snapshotFieldLabel = (key: string, custom = false) =>
  SNAPSHOT_FIELD_LABELS[key] || (custom ? `自定义-${key}` : key);

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

const getSnapshotFieldDefinitions = (
  snapshot: JsonRecord[],
  scope: string,
): SnapshotFieldDefinition[] => {
  const definitions = snapshot
    .filter(
      (field) =>
        field.scope === scope && typeof field.itemKey === "string",
    )
    .map((field) => {
      const itemKey = field.itemKey as string;
      return {
        scope,
        itemKey,
        title:
          typeof field.title === "string" && field.title.trim()
            ? field.title
            : snapshotFieldLabel(itemKey),
        groupLabel:
          typeof field.groupLabel === "string" && field.groupLabel.trim()
            ? field.groupLabel
            : undefined,
      };
    });
  return definitions.filter(
    (field, index, fields) =>
      fields.findIndex(
        (candidate) => candidate.itemKey === field.itemKey,
      ) === index,
  );
};

const SnapshotValue = ({ value }: { value: unknown }) => {
  if (Array.isArray(value)) {
    if (!value.length) return <Typography.Text type="secondary">-</Typography.Text>;
    if (value.every((item) => !isJsonRecord(item))) {
      return (
        <Space size={[4, 4]} wrap>
          {value.map((item, index) => (
            <Tag key={`${valueText(item)}-${index}`}>{valueText(item)}</Tag>
          ))}
        </Space>
      );
    }
  }

  return (
    <Typography.Text copyable={typeof value === "string"}>
      {valueText(value)}
    </Typography.Text>
  );
};

const SnapshotValueTable = ({ values }: { values: JsonRecord[] }) => {
  const responsive = useResponsive();
  const columns = Array.from(
    new Set(values.flatMap((value) => Object.keys(value))),
  );

  if (!columns.length) return <Typography.Text type="secondary">-</Typography.Text>;

  return (
    <DataTable<JsonRecord>
      rowKey={(_, index) => String(index)}
      isMobile={responsive.isMobile}
      size="small"
      bordered
      pagination={false}
      tableLayout="fixed"
      scroll={columns.length > 3 ? { x: columns.length * 140 } : undefined}
      dataSource={values}
      columns={columns.map((key) => ({
        title: snapshotFieldLabel(key),
        dataIndex: key,
        width: columns.length > 3 ? 140 : undefined,
        render: (value: unknown) => <SnapshotValue value={value} />,
      }))}
    />
  );
};

const SnapshotRecordList = ({
  values,
  fieldDefinitions = [],
}: {
  values: JsonRecord[];
  fieldDefinitions?: SnapshotFieldDefinition[];
}) => (
  <Space
    className="competition-registration-data-page__snapshot-record-list"
    orientation="vertical"
    size={8}
  >
    {values.map((record, index) => {
      const definitionByKey = new Map(
        fieldDefinitions.map((field) => [field.itemKey, field]),
      );
      const configuredFields = fieldDefinitions.map((field) => ({
        key: field.itemKey,
        label: field.title,
        value: record[field.itemKey],
      }));
      const recordFields = Object.entries(record)
        .filter(([key]) => key !== "extraValues")
        .filter(([key]) => !definitionByKey.has(key))
        .map(([key, value]) => ({
          key,
          label: snapshotFieldLabel(key),
          value,
        }));
      const extraFields = isJsonRecord(record.extraValues)
        ? Object.entries(record.extraValues)
            .filter(([key]) => !definitionByKey.has(key))
            .map(([key, value]) => ({
              key: `extra-${key}`,
              label: snapshotFieldLabel(key, true),
              value,
            }))
        : [];
      const fields = [...configuredFields, ...recordFields, ...extraFields];
      return (
        <div
          key={`snapshot-record-${index + 1}`}
          className="competition-registration-data-page__snapshot-record"
        >
          {values.length > 1 ? (
            <Typography.Text strong>知识产权 {index + 1}</Typography.Text>
          ) : null}
          {fields.length ? (
            <Descriptions
              bordered
              size="small"
              column={1}
              items={fields.map(({ key, label, value }) => ({
                key,
                label,
                children: <SnapshotValue value={value} />,
              }))}
            />
          ) : (
            <Typography.Text type="secondary">-</Typography.Text>
          )}
        </div>
      );
    })}
  </Space>
);

const readMemberFieldValue = (member: JsonRecord, fieldKey: string) => {
  if (Object.prototype.hasOwnProperty.call(member, fieldKey)) {
    return member[fieldKey];
  }
  if (isJsonRecord(member.extraValues)) {
    return member.extraValues[fieldKey];
  }
  if (typeof member.extraValuesJson === "string") {
    return parseJson<JsonRecord>(member.extraValuesJson, {})[fieldKey];
  }
  return undefined;
};

const DetailSection = ({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) => (
  <section className="competition-registration-data-page__detail-section">
    <Typography.Title
      level={5}
      className="competition-registration-data-page__detail-section-title"
    >
      {title}
    </Typography.Title>
    {children}
  </section>
);

const renderSnapshotValue = (
  value: unknown,
  fieldKey?: string,
  fieldDefinitions: SnapshotFieldDefinition[] = [],
): ReactNode => {
  if (
    fieldKey === "intellectualProperties" &&
    Array.isArray(value) &&
    value.every(isJsonRecord)
  ) {
    return (
      <SnapshotRecordList
        values={value}
        fieldDefinitions={fieldDefinitions.filter((field) => Boolean(field.groupLabel))}
      />
    );
  }
  if (Array.isArray(value) && value.every(isJsonRecord)) {
    return <SnapshotValueTable values={value} />;
  }
  return <SnapshotValue value={value} />;
};

const SnapshotCard = ({
  title,
  values,
  fieldDefinitions = [],
  emptyDescription = "暂无数据",
}: {
  title: string;
  values: JsonRecord;
  fieldDefinitions?: SnapshotFieldDefinition[];
  emptyDescription?: string;
}) => {
  const extras =
    values.extraValues &&
    typeof values.extraValues === "object" &&
    !Array.isArray(values.extraValues)
      ? Object.entries(values.extraValues as JsonRecord)
      : [];
  const directDefinitions = fieldDefinitions.filter(
    (field) => !field.groupLabel,
  );
  const definitionByKey = new Map(
    directDefinitions.map((field) => [field.itemKey, field]),
  );
  const items = [
    ...directDefinitions.map((field) => ({
      key: field.itemKey,
      fieldKey: field.itemKey,
      label: field.title,
      value: Object.prototype.hasOwnProperty.call(values, field.itemKey)
        ? values[field.itemKey]
        : Object.fromEntries(extras)[field.itemKey],
    })),
    ...Object.entries(values)
      .filter(([key]) => key !== "extraValues")
      .filter(([key]) => !definitionByKey.has(key))
      .map(([key, value]) => ({
        key,
        fieldKey: key,
        label: snapshotFieldLabel(key),
        value,
      })),
    ...extras
      .filter(([key]) => !definitionByKey.has(key))
      .map(([key, value]) => ({
        key: `custom-${key}`,
        fieldKey: key,
        label: snapshotFieldLabel(key, true),
        value,
      })),
  ];
  return (
    <DetailSection title={title}>
      {items.length ? (
        <Descriptions
          bordered
          size="small"
          column={1}
          items={items.map(({ key, fieldKey, label, value }) => ({
            key,
            label,
            children: renderSnapshotValue(value, fieldKey, fieldDefinitions),
          }))}
        />
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={emptyDescription}
        />
      )}
    </DetailSection>
  );
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
  const [readyDownload, setReadyDownload] =
    useState<ReadyRegistrationExportDownload>();
  const [downloadingReadyFile, setDownloadingReadyFile] = useState(false);
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
          const download = createReadyRegistrationExportDownload(
            task.downloadUrl,
            task.fileName || started.fileName,
          );
          setReadyDownload(download);
          await downloadReadyRegistrationExport(download);
          message.success(
            `报名记录 Excel 已生成并已提交浏览器下载；如未看到下载提示，可点击“重新下载最近文件”`,
          );
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
          const download = createReadyRegistrationExportDownload(
            task.downloadUrl,
            task.fileName || started.fileName,
          );
          setReadyDownload(download);
          await downloadReadyRegistrationExport(download);
          message.success(
            `完整材料包已生成并已提交浏览器下载；如未看到下载提示，可点击“重新下载最近文件”`,
          );
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

  const downloadLatestReadyFile = async () => {
    if (!readyDownload) return;
    setDownloadingReadyFile(true);
    try {
      await downloadReadyRegistrationExport(readyDownload);
      message.success(`已重新提交浏览器下载：${readyDownload.fileName}`);
    } catch (error) {
      showErrorMessage(error, "最近生成文件下载失败");
    } finally {
      setDownloadingReadyFile(false);
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
      saveBlobAsFile(blob, `${value.fieldKey}-${value.fileId}`);
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
            orientation="vertical"
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
  const { students: studentMemberValues, teachers: teacherMemberValues } =
    splitRegistrationMemberSnapshots(memberValues);
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
        ...(readyDownload
          ? [
              <RecentRegistrationExportDownloadButton
                key="download-latest-ready-file"
                download={readyDownload}
                busy={exporting || packaging}
                loading={downloadingReadyFile}
                onDownload={() => void downloadLatestReadyFile()}
              />,
            ]
          : []),
      ]
    : [];

  const tableRequest = useMemo(
    () => buildTableRequest<CompetitionRegistrationRecord>(async (params) => {
      const query: RegistrationExportQuery = {
        status: typeof params.status === "string" ? params.status : undefined,
        keyword: typeof params.keyword === "string" ? params.keyword.trim() || undefined : undefined,
      };
      const querySignature = buildRegistrationQuerySignature(query);
      if (lastQuerySignatureRef.current !== querySignature) {
        lastQuerySignatureRef.current = querySignature;
        clearSelection();
        setResultTotal(0);
      }
      setLastQuery(query);
      setResultTotal(0);
      const response = await listCompetitionWorkspaceRegistrations(workspaceUuid, {
        status: query.status,
        keyword: query.keyword,
        includeSnapshots: true,
        pageNo: params.pageNo,
        pageSize: params.pageSize,
      });
      setResultTotal(response.total || 0);
      return { records: response.records, total: response.total };
    }),
    [clearSelection, workspaceUuid],
  );

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
        request={tableRequest}
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

  const collectionSchemaSnapshot = useMemo(
    () => parseJson<JsonRecord[]>(detail?.collectionSchemaSnapshotJson, []),
    [detail?.collectionSchemaSnapshotJson],
  );

  const fieldDefinitionsByScope = useMemo(() => {
    const scopes = [
      "REGISTRATION_FIELD",
      "TEAM_FIELD",
      "MEMBER_FIELD",
      "TEACHER_FIELD",
      "PROJECT_FIELD",
    ];
    return scopes.reduce<Record<string, SnapshotFieldDefinition[]>>(
      (result, scope) => {
        result[scope] = getSnapshotFieldDefinitions(
          collectionSchemaSnapshot,
          scope,
        );
        return result;
      },
      {},
    );
  }, [collectionSchemaSnapshot]);

  const studentMemberFieldDefinitions = useMemo(() => {
    const definitions = [
      ...(fieldDefinitionsByScope.MEMBER_FIELD || []),
    ];
    if (!definitions.some((field) => field.itemKey === "memberName")) {
      definitions.unshift({
        scope: "MEMBER_FIELD",
        itemKey: "memberName",
        title: "姓名",
      });
    }
    return definitions.filter((field, index, fields) => (
      fields.findIndex((candidate) => candidate.itemKey === field.itemKey) === index
    ));
  }, [fieldDefinitionsByScope]);

  const teacherMemberFieldDefinitions = useMemo(() => {
    const definitions = [
      ...(fieldDefinitionsByScope.TEACHER_FIELD || []),
    ];
    if (!definitions.some((field) => field.itemKey === "memberName")) {
      definitions.unshift({
        scope: "TEACHER_FIELD",
        itemKey: "memberName",
        title: "姓名",
      });
    }
    return definitions.filter((field, index, fields) => (
      fields.findIndex((candidate) => candidate.itemKey === field.itemKey) === index
    ));
  }, [fieldDefinitionsByScope]);

  const registrationFieldDefinitions =
    fieldDefinitionsByScope.REGISTRATION_FIELD || [];
  const teamFieldDefinitions = fieldDefinitionsByScope.TEAM_FIELD || [];
  const projectFieldDefinitions = fieldDefinitionsByScope.PROJECT_FIELD || [];

  const studentMemberColumns = useMemo<TableColumnsType<JsonRecord>>(
    () => studentMemberFieldDefinitions.map(({ itemKey, title }) => ({
      key: itemKey,
      title,
      dataIndex: itemKey,
      render: (_value, member) => valueText(readMemberFieldValue(member, itemKey)),
    })),
    [studentMemberFieldDefinitions],
  );

  const teacherMemberColumns = useMemo<TableColumnsType<JsonRecord>>(
    () => teacherMemberFieldDefinitions.map(({ itemKey, title }) => ({
      key: itemKey,
      title,
      dataIndex: itemKey,
      render: (_value, member) => valueText(readMemberFieldValue(member, itemKey)),
    })),
    [teacherMemberFieldDefinitions],
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
            orientation="vertical"
            size={16}
          >
            <DetailSection title="报名概览">
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
            </DetailSection>
            <SnapshotCard
              title="报名信息"
              values={registrationValues}
              fieldDefinitions={registrationFieldDefinitions}
              emptyDescription="该赛事未配置报名信息字段"
            />
            <SnapshotCard
              title="团队信息"
              values={teamValues}
              fieldDefinitions={teamFieldDefinitions}
            />
            <SnapshotCard
              title="项目信息"
              values={projectValues}
              fieldDefinitions={projectFieldDefinitions}
            />
            <DetailSection title={`学生成员（${studentMemberValues.length}）`}>
              <DataTable<JsonRecord>
                rowKey={(_, index) => String(index)}
                isMobile={responsive.isMobile}
                size="small"
                pagination={false}
                scroll={studentMemberColumns.length > 4 ? { x: 900 } : undefined}
                dataSource={studentMemberValues}
                columns={studentMemberColumns}
              />
            </DetailSection>
            <DetailSection title={`指导教师（${teacherMemberValues.length}）`}>
              <DataTable<JsonRecord>
                rowKey={(_, index) => String(index)}
                isMobile={responsive.isMobile}
                size="small"
                pagination={false}
                scroll={teacherMemberColumns.length > 4 ? { x: 900 } : undefined}
                dataSource={teacherMemberValues}
                columns={teacherMemberColumns}
              />
            </DetailSection>
            <DetailSection title={`阶段材料（${materials.length} 次提交）`}>
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
                    <DataTable<CompetitionMaterialValueRecord>
                      rowKey="id"
                      isMobile={responsive.isMobile}
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
            </DetailSection>
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
