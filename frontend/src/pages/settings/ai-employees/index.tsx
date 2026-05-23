import {
  AppstoreOutlined,
  CustomerServiceOutlined,
  MinusOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  SyncOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import { history, useLocation } from '@umijs/max';
import {
  Alert,
  Avatar,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  InputNumber,
  List,
  Radio,
  Row,
  Select,
  Space,
  Switch,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { STANDARD_DRAWER_WIDTH } from '@/constants/ui';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { ManagementDrawer, ManagementPage, ManagementPageBody, ManagementTable } from '@/features/management';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { buildTableRequest } from '@/features/table/proTable';
import { TableActionBar } from '@/features/table/TableActionBar';
import { confirmAction } from '@/utils/confirm';
import { aiService } from '@/services/ai';
import type {
  AiEmployeeDetailRecord,
  AiEmployeeRecord,
  AiEmployeeSkillRecord,
  AiLlmServiceRecord,
  AiLlmServiceTestResult,
  AiSkillPermissionMode,
  AiSkillRecord,
} from '@/types/api';

type AiPageTabKey = 'employees' | 'llm-services';
type LlmFormValues = {
  provider?: string;
  code?: string;
  title?: string;
  baseUrl?: string;
  apiKey?: string;
  defaultModel?: string;
  enabled?: boolean;
  timeoutMs?: number;
  temperature?: number;
  maxTokens?: number;
};

type AvatarOption = {
  key: string;
  label: string;
  color: string;
  icon: ReactNode;
};

const DEFAULT_AVATAR_KEY = 'avatar-purple-01';
const EMPLOYEE_TAB_KEY: AiPageTabKey = 'employees';
const LLM_TAB_KEY: AiPageTabKey = 'llm-services';

const AVATAR_OPTIONS: AvatarOption[] = [
  { key: 'avatar-purple-01', label: '紫色', color: '#6E56CF', icon: <RobotOutlined /> },
  { key: 'avatar-blue-01', label: '蓝色', color: '#1677ff', icon: <UserOutlined /> },
  { key: 'avatar-green-01', label: '绿色', color: '#13c2c2', icon: <TeamOutlined /> },
  { key: 'avatar-orange-01', label: '橙色', color: '#fa8c16', icon: <CustomerServiceOutlined /> },
  { key: 'avatar-red-01', label: '红色', color: '#ff4d4f', icon: <SafetyCertificateOutlined /> },
  { key: 'avatar-gray-01', label: '灰色', color: '#8c8c8c', icon: <AppstoreOutlined /> },
];

const PROVIDER_OPTIONS = [
  { label: '阿里云百炼', value: 'aliyun-bailian' },
  { label: 'DashScope（兼容旧配置）', value: 'dashscope' },
  { label: 'DeepSeek', value: 'deepseek' },
  { label: 'OpenAI Compatible', value: 'openai-compatible' },
  { label: 'Ollama', value: 'ollama' },
];

const PROVIDER_DEFAULTS: Record<string, { baseUrl: string; defaultModel: string }> = {
  'aliyun-bailian': {
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    defaultModel: 'qwen-plus',
  },
  dashscope: {
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    defaultModel: 'qwen-plus',
  },
  deepseek: {
    baseUrl: 'https://api.deepseek.com',
    defaultModel: 'deepseek-v4-flash',
  },
  'openai-compatible': {
    baseUrl: 'https://api.openai.com/v1',
    defaultModel: '',
  },
  ollama: {
    baseUrl: 'http://localhost:11434/v1',
    defaultModel: '',
  },
};

const PERMISSION_MODE_OPTIONS: Array<{ label: string; value: AiSkillPermissionMode }> = [
  { label: '访问', value: 'visit' },
  { label: '允许', value: 'allow' },
  { label: '禁用', value: 'deny' },
];

const PERMISSION_MODE_COLOR: Record<AiSkillPermissionMode, string> = {
  visit: 'blue',
  allow: 'green',
  deny: 'red',
};

const RISK_LEVEL_COLOR: Record<string, string> = {
  LOW: 'green',
  MEDIUM: 'orange',
  HIGH: 'red',
};

const parseTabKey = (value?: string | null): AiPageTabKey => {
  if (value === LLM_TAB_KEY) {
    return value;
  }
  return EMPLOYEE_TAB_KEY;
};

const buildSkillModeMap = (
  catalog: AiSkillRecord[],
  selectedSkills?: AiEmployeeSkillRecord[] | null,
) => {
  const map: Record<string, AiSkillPermissionMode> = {};
  catalog
    .filter((skill) => !skill.readOnly)
    .forEach((skill) => {
      const matchedSkill = selectedSkills?.find((item) => item.skillCode === skill.skillCode);
      map[skill.skillCode] = matchedSkill?.permissionMode || 'deny';
    });
  return map;
};

const getAvatarOption = (avatarKey?: string | null) =>
  AVATAR_OPTIONS.find((option) => option.key === avatarKey) || AVATAR_OPTIONS[0];

const getPermissionModeTag = (mode: AiSkillPermissionMode) => (
  <Tag color={PERMISSION_MODE_COLOR[mode]}>{mode === 'visit' ? '访问' : mode === 'allow' ? '允许' : '禁用'}</Tag>
);

const SkillSection = ({
  title,
  skills,
  readOnly,
  modes,
  onModeChange,
}: {
  title: string;
  skills: AiSkillRecord[];
  readOnly: boolean;
  modes: Record<string, AiSkillPermissionMode>;
  onModeChange: (skillCode: string, mode: AiSkillPermissionMode) => void;
}) => {
  if (!skills.length) {
    return (
      <Card size="small" title={title}>
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无技能" />
      </Card>
    );
  }

  return (
    <Card size="small" title={title}>
      <List
        dataSource={skills}
        split
        renderItem={(skill) => {
          const selectedMode = modes[skill.skillCode] || (skill.readOnly ? 'visit' : 'deny');
          return (
            <List.Item style={{ display: 'block', paddingInline: 0 }}>
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                <Space wrap>
                  <Typography.Text strong>{skill.skillName}</Typography.Text>
                  <Tag>{skill.skillCode}</Tag>
                  {skill.category ? <Tag color="default">{skill.category}</Tag> : null}
                  {skill.riskLevel ? <Tag color={RISK_LEVEL_COLOR[skill.riskLevel] || 'default'}>{skill.riskLevel}</Tag> : null}
                  {skill.needConfirm ? <Tag color="volcano">需二次确认</Tag> : null}
                  {readOnly ? <Tag color="blue">通用技能</Tag> : <Tag color="purple">自定义技能</Tag>}
                  {skill.readOnly ? <Tag color="cyan">只读</Tag> : null}
                </Space>
                <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                  {skill.description || '暂无描述'}
                </Typography.Paragraph>
                <Space wrap align="center">
                  <Typography.Text type="secondary">权限模式</Typography.Text>
                  {readOnly ? (
                    getPermissionModeTag('visit')
                  ) : (
                    <Select
                      value={selectedMode}
                      options={PERMISSION_MODE_OPTIONS}
                      style={{ minWidth: 140 }}
                      onChange={(mode) => onModeChange(skill.skillCode, mode)}
                    />
                  )}
                </Space>
              </Space>
            </List.Item>
          );
        }}
      />
    </Card>
  );
};

const AiEmployeesPage = () => {
  const location = useLocation();
  const { actionPermission, responsive, buildToolbarButtons } = usePagePermissionActions();
  const [employeeForm] = Form.useForm();
  const [llmForm] = Form.useForm();
  const [employeeSkillCatalog, setEmployeeSkillCatalog] = useState<AiSkillRecord[]>([]);
  const [employeePromptTemplate, setEmployeePromptTemplate] = useState('');
  const [llmServiceOptions, setLlmServiceOptions] = useState<Array<{ label: string; value: number }>>([]);
  const [employeeSkillModes, setEmployeeSkillModes] = useState<Record<string, AiSkillPermissionMode>>({});
  const [selectedLlmService, setSelectedLlmService] = useState<AiLlmServiceRecord | null>(null);
  const [employeeSaving, setEmployeeSaving] = useState(false);
  const [llmSaving, setLlmSaving] = useState(false);
  const [llmTesting, setLlmTesting] = useState(false);
  const [llmTestResult, setLlmTestResult] = useState<AiLlmServiceTestResult | null>(null);
  const [bootstrapLoading, setBootstrapLoading] = useState(true);

  const employeeState = useCrudPageState<AiEmployeeRecord>();
  const llmState = useCrudPageState<AiLlmServiceRecord>();
  const searchParams = useMemo(() => new URLSearchParams(location.search), [location.search]);
  const activeTab = parseTabKey(searchParams.get('tab'));

  const commonSkills = useMemo(() => employeeSkillCatalog.filter((skill) => Boolean(skill.readOnly)), [employeeSkillCatalog]);
  const customSkills = useMemo(() => employeeSkillCatalog.filter((skill) => !skill.readOnly), [employeeSkillCatalog]);

  useEffect(() => {
    let active = true;

    const loadBootstrapData = async () => {
      setBootstrapLoading(true);
      try {
        const [skills, template, services] = await Promise.all([
          aiService.skills({ autoRedirectOnUnauthorized: false }),
          aiService.employeePromptTemplate({ autoRedirectOnUnauthorized: false }),
          aiService.llmServices({ pageNo: 1, pageSize: 200 }, { autoRedirectOnUnauthorized: false }),
        ]);

        if (!active) {
          return;
        }

        setEmployeeSkillCatalog(skills);
        setEmployeePromptTemplate(template.defaultSystemPromptTemplate);
        setLlmServiceOptions((services.records || []).map((service) => ({
          label: `${service.title} (${service.code})`,
          value: service.id,
        })));
      } catch (error) {
        if (active) {
          message.error(error instanceof Error && error.message ? error.message : '加载数字员工基础数据失败');
        }
      } finally {
        if (active) {
          setBootstrapLoading(false);
        }
      }
    };

    void loadBootstrapData();

    return () => {
      active = false;
    };
  }, []);

  const syncTab = (tab: AiPageTabKey) => {
    const nextSearch = new URLSearchParams(location.search);
    nextSearch.set('tab', tab);
    history.replace(`${location.pathname}?${nextSearch.toString()}`);
  };

  const setSkillModesFromDetail = (detail?: AiEmployeeDetailRecord | null) => {
    setEmployeeSkillModes(buildSkillModeMap(employeeSkillCatalog, detail?.skills || []));
  };

  const openEmployeeCreate = () => {
    employeeState.drawer.openCreate();
    employeeForm.resetFields();
    employeeForm.setFieldsValue({
      username: '',
      nickname: '',
      position: '',
      avatarKey: DEFAULT_AVATAR_KEY,
      description: '',
      greeting: '',
      systemPrompt: employeePromptTemplate,
      defaultLlmServiceId: undefined,
    });
    setSkillModesFromDetail(null);
  };

  const openEmployeeEdit = async (record: AiEmployeeRecord) => {
    employeeState.drawer.openEdit(record, record.id);
    try {
      const detail = await aiService.employee(record.id, { autoRedirectOnUnauthorized: false });
      employeeForm.setFieldsValue({
        username: detail.username,
        nickname: detail.nickname,
        position: detail.position || '',
        avatarKey: detail.avatarKey || DEFAULT_AVATAR_KEY,
        description: detail.description || '',
        greeting: detail.greeting || '',
        systemPrompt: detail.systemPrompt ?? detail.defaultSystemPromptTemplate ?? employeePromptTemplate,
        defaultLlmServiceId: detail.defaultLlmServiceId || undefined,
      });
      setSkillModesFromDetail(detail);
    } catch {
      employeeState.drawer.reset();
    }
  };

  const saveEmployee = async () => {
    setEmployeeSaving(true);
    try {
      const values = await employeeForm.validateFields();
      const payload = {
        username: String(values.username || '').trim(),
        nickname: String(values.nickname || '').trim(),
        position: values.position?.trim?.() ? values.position.trim() : null,
        avatarKey: values.avatarKey || DEFAULT_AVATAR_KEY,
        description: values.description?.trim?.() ? values.description.trim() : null,
        greeting: values.greeting?.trim?.() ? values.greeting.trim() : null,
        systemPrompt: values.systemPrompt?.trim?.() ? values.systemPrompt.trim() : null,
        defaultLlmServiceId: values.defaultLlmServiceId || null,
        sortOrder: 0,
        skills: customSkills.map((skill) => ({
          skillCode: skill.skillCode,
          permissionMode: employeeSkillModes[skill.skillCode] || 'deny',
        })),
      };

      if (employeeState.drawer.editingId) {
        await aiService.updateEmployee(employeeState.drawer.editingId, payload, { autoRedirectOnUnauthorized: false });
        message.success('数字员工已更新');
      } else {
        await aiService.createEmployee(payload, { autoRedirectOnUnauthorized: false });
        message.success('数字员工已创建');
      }

      employeeState.drawer.reset();
      employeeState.reloadTable();
    } finally {
      setEmployeeSaving(false);
    }
  };

  const toggleEmployeeEnabled = async (record: AiEmployeeRecord) => {
    const nextEnabled = !record.enabled;
    if (!nextEnabled) {
      confirmAction({
        title: '禁用数字员工',
        content: `确认禁用数字员工「${record.nickname || record.username}」吗？`,
        okText: '确认禁用',
        okButtonProps: { danger: true },
        onOk: async () => {
          await aiService.updateEmployeeEnabled(record.id, false, { autoRedirectOnUnauthorized: false });
          message.success('状态已更新');
          employeeState.reloadTable();
        },
      });
      return;
    }

    await aiService.updateEmployeeEnabled(record.id, true, { autoRedirectOnUnauthorized: false });
    message.success('状态已更新');
    employeeState.reloadTable();
  };

  const deleteEmployee = (record: AiEmployeeRecord) => {
    confirmAction({
      title: '删除数字员工',
      content: `确认删除数字员工「${record.nickname || record.username}」吗？`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await aiService.deleteEmployee(record.id, { autoRedirectOnUnauthorized: false });
        message.success('数字员工已删除');
        employeeState.reloadTable();
      },
    });
  };

  const openLlmCreate = () => {
    llmState.drawer.openCreate();
    setSelectedLlmService(null);
    setLlmTestResult(null);
    llmForm.resetFields();
    llmForm.setFieldsValue({
      provider: 'aliyun-bailian',
      code: '',
      title: '',
      baseUrl: PROVIDER_DEFAULTS['aliyun-bailian'].baseUrl,
      apiKey: '',
      defaultModel: PROVIDER_DEFAULTS['aliyun-bailian'].defaultModel,
      enabled: true,
      timeoutMs: 60000,
      temperature: 0.7,
      maxTokens: 2048,
    });
  };

  const openLlmEdit = async (record: AiLlmServiceRecord) => {
    llmState.drawer.openEdit(record, record.id);
    setLlmTestResult(null);
    try {
      const detail = await aiService.llmService(record.id, { autoRedirectOnUnauthorized: false });
      setSelectedLlmService(detail);
      llmForm.setFieldsValue({
        provider: detail.provider,
        code: detail.code,
        title: detail.title,
        baseUrl: detail.baseUrl || '',
        apiKey: '',
        defaultModel: detail.defaultModel || '',
        enabled: Boolean(detail.enabled),
        timeoutMs: detail.timeoutMs ?? 60000,
        temperature: detail.temperature ?? 0.7,
        maxTokens: detail.maxTokens ?? 2048,
      });
    } catch {
      llmState.drawer.reset();
      setSelectedLlmService(null);
    }
  };

  const handleProviderChange = (provider: string) => {
    const defaults = PROVIDER_DEFAULTS[provider];
    if (!defaults) {
      return;
    }
    setLlmTestResult(null);
    llmForm.setFieldsValue({
      baseUrl: defaults.baseUrl,
      defaultModel: defaults.defaultModel || llmForm.getFieldValue('defaultModel'),
    });
  };

  const buildLlmPayload = (values: LlmFormValues) => ({
    provider: String(values.provider || '').trim(),
    code: String(values.code || '').trim(),
    title: String(values.title || '').trim(),
    baseUrl: values.baseUrl?.trim?.() ? values.baseUrl.trim() : null,
    apiKey: values.apiKey?.trim?.() ? values.apiKey.trim() : null,
    defaultModel: values.defaultModel?.trim?.() ? values.defaultModel.trim() : null,
    enabled: Boolean(values.enabled),
    timeoutMs: values.timeoutMs ?? 60000,
    temperature: values.temperature ?? 0.7,
    maxTokens: values.maxTokens ?? 2048,
  });

  const saveLlmService = async () => {
    setLlmSaving(true);
    try {
      const values = await llmForm.validateFields();
      const payload = buildLlmPayload(values);

      if (llmState.drawer.editingId) {
        await aiService.updateLlmService(llmState.drawer.editingId, payload, { autoRedirectOnUnauthorized: false });
        message.success('LLM 服务已更新');
      } else {
        await aiService.createLlmService(payload, { autoRedirectOnUnauthorized: false });
        message.success('LLM 服务已创建');
      }

      llmState.drawer.reset();
      setSelectedLlmService(null);
      llmState.reloadTable();
      const services = await aiService.llmServices({ pageNo: 1, pageSize: 200 }, { autoRedirectOnUnauthorized: false });
      setLlmServiceOptions((services.records || []).map((service) => ({
        label: `${service.title} (${service.code})`,
        value: service.id,
      })));
    } finally {
      setLlmSaving(false);
    }
  };

  const testLlmService = async () => {
    setLlmTesting(true);
    setLlmTestResult(null);
    try {
      await llmForm.validateFields(['provider']);
      const values = llmForm.getFieldsValue();
      const result = await aiService.testLlmService(
        {
          ...buildLlmPayload(values),
          serviceId: llmState.drawer.editingId || selectedLlmService?.id || null,
        },
        { autoRedirectOnUnauthorized: false, silent: true },
      );
      setLlmTestResult(result);
      if (result.success) {
        message.success('LLM 服务测试通过');
      } else {
        message.warning(result.message || 'LLM 服务测试失败');
      }
    } catch (error) {
      const errorMessage = error instanceof Error && error.message ? error.message : 'LLM 服务测试失败';
      setLlmTestResult({ success: false, message: errorMessage });
      message.error(errorMessage);
    } finally {
      setLlmTesting(false);
    }
  };

  const toggleLlmServiceEnabled = async (record: AiLlmServiceRecord) => {
    const nextEnabled = !record.enabled;
    if (!nextEnabled) {
      confirmAction({
        title: '禁用 LLM 服务',
        content: `确认禁用 LLM 服务「${record.title}」吗？`,
        okText: '确认禁用',
        okButtonProps: { danger: true },
        onOk: async () => {
          await aiService.updateLlmServiceEnabled(record.id, false, { autoRedirectOnUnauthorized: false });
          message.success('状态已更新');
          llmState.reloadTable();
        },
      });
      return;
    }

    await aiService.updateLlmServiceEnabled(record.id, true, { autoRedirectOnUnauthorized: false });
    message.success('状态已更新');
    llmState.reloadTable();
  };

  const deleteLlmService = (record: AiLlmServiceRecord) => {
    confirmAction({
      title: '删除 LLM 服务',
      content: `确认删除 LLM 服务「${record.title}」吗？`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await aiService.deleteLlmService(record.id, { autoRedirectOnUnauthorized: false });
        message.success('LLM 服务已删除');
        llmState.reloadTable();
        const services = await aiService.llmServices({ pageNo: 1, pageSize: 200 }, { autoRedirectOnUnauthorized: false });
        setLlmServiceOptions((services.records || []).map((service) => ({
          label: `${service.title} (${service.code})`,
          value: service.id,
        })));
      },
    });
  };

  const employeeColumns = useMemo<ProColumns<AiEmployeeRecord>[]>(
    () => [
      {
        title: '头像',
        dataIndex: 'avatarKey',
        width: 96,
        render: (_, record) => {
          const avatar = getAvatarOption(record.avatarKey);
          return (
            <Space>
              <Avatar style={{ backgroundColor: avatar.color }} icon={avatar.icon} />
              <Typography.Text type="secondary">{avatar.label}</Typography.Text>
            </Space>
          );
        },
      },
      { title: '用户名', dataIndex: 'username', width: 180 },
      { title: '昵称', dataIndex: 'nickname', width: 180 },
      { title: '职位', dataIndex: 'position', width: 160, ellipsis: true, render: (_, record) => record.position || '-' },
      {
        title: '启用状态',
        dataIndex: 'enabled',
        width: 120,
        render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{record.enabled ? '已启用' : '已禁用'}</Tag>,
      },
      {
        title: '操作',
        valueType: 'option',
        fixed: responsive.isDesktop ? 'right' : undefined,
        width: 180,
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={actionPermission.buildTableActions([
              { key: 'edit', label: '编辑', onClick: () => void openEmployeeEdit(record), permission: 'ai:employee:update' },
              {
                key: 'toggle',
                label: record.enabled ? '禁用' : '启用',
                onClick: () => void toggleEmployeeEnabled(record),
                permission: 'ai:employee:status',
                unauthorizedMode: 'hide',
              },
              { key: 'delete', label: '删除', onClick: () => deleteEmployee(record), permission: 'ai:employee:delete', danger: true },
            ])}
          />
        ),
      },
    ],
    [actionPermission, responsive.isDesktop, responsive.isMobile],
  );

  const llmColumns = useMemo<ProColumns<AiLlmServiceRecord>[]>(
    () => [
      { title: '唯一标识', dataIndex: 'code', width: 180 },
      { title: '标题', dataIndex: 'title', width: 220, ellipsis: true },
      { title: 'LLM 类型', dataIndex: 'provider', width: 180 },
      { title: '默认模型', dataIndex: 'defaultModel', width: 180, render: (_, record) => record.defaultModel || '-' },
      {
        title: '启用状态',
        dataIndex: 'enabled',
        width: 120,
        render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{record.enabled ? '已启用' : '已禁用'}</Tag>,
      },
      {
        title: '操作',
        valueType: 'option',
        fixed: responsive.isDesktop ? 'right' : undefined,
        width: 180,
        render: (_, record) => (
          <TableActionBar
            isMobile={responsive.isMobile}
            items={actionPermission.buildTableActions([
              { key: 'edit', label: '编辑', onClick: () => void openLlmEdit(record), permission: 'ai:llm:update' },
              {
                key: 'toggle',
                label: record.enabled ? '禁用' : '启用',
                onClick: () => void toggleLlmServiceEnabled(record),
                permission: 'ai:llm:status',
                unauthorizedMode: 'hide',
              },
              { key: 'delete', label: '删除', onClick: () => deleteLlmService(record), permission: 'ai:llm:delete', danger: true },
            ])}
          />
        ),
      },
    ],
    [actionPermission, responsive.isDesktop, responsive.isMobile],
  );

  const employeeTab = (
    <ManagementTable<AiEmployeeRecord>
      actionRef={employeeState.actionRef}
      rowKey="id"
      columns={employeeColumns}
      isMobile={responsive.isMobile}
      loading={bootstrapLoading}
      search={false}
      request={buildTableRequest((params) => aiService.employees(params, { autoRedirectOnUnauthorized: false }))}
      toolBarRender={false}
    />
  );

  const llmTab = (
    <ManagementTable<AiLlmServiceRecord>
      actionRef={llmState.actionRef}
      rowKey="id"
      columns={llmColumns}
      isMobile={responsive.isMobile}
      loading={bootstrapLoading}
      search={false}
      request={buildTableRequest((params) => aiService.llmServices(params, { autoRedirectOnUnauthorized: false }))}
      toolBarRender={false}
    />
  );

  const employeeDrawerTitle = employeeState.drawer.editingId ? '编辑 AI 员工' : '新建 AI 员工';
  const llmDrawerTitle = llmState.drawer.editingId ? '编辑 LLM 服务' : '新建 LLM 服务';

  return (
    <ManagementPage className="saas-crud-page" ghost title="数字员工" style={{ height: '100%', minHeight: 0 }} content={null}>
      <ManagementPageBody>
        <Card className="saas-ai-employees-card" bodyStyle={{ paddingTop: 8 }}>
          <Tabs
            activeKey={activeTab}
            destroyInactiveTabPane={false}
            onChange={(key) => syncTab(key as AiPageTabKey)}
            tabBarExtraContent={
              <Space wrap>
                {activeTab === EMPLOYEE_TAB_KEY
                  ? buildToolbarButtons([
                      {
                        key: 'create-employee',
                        label: '新建 AI 员工',
                        type: 'primary',
                        onClick: openEmployeeCreate,
                        permission: 'ai:employee:create',
                      },
                      {
                        key: 'refresh-employee',
                        label: '刷新',
                        onClick: employeeState.reloadTable,
                      },
                    ])
                  : buildToolbarButtons([
                      {
                        key: 'create-llm',
                        label: '新增 LLM 服务',
                        type: 'primary',
                        onClick: openLlmCreate,
                        permission: 'ai:llm:create',
                      },
                      {
                        key: 'refresh-llm',
                        label: '刷新',
                        onClick: llmState.reloadTable,
                      },
                    ])}
              </Space>
            }
            items={[
              { key: EMPLOYEE_TAB_KEY, label: 'AI 员工', children: employeeTab },
              { key: LLM_TAB_KEY, label: 'LLM 服务', children: llmTab },
            ]}
          />
        </Card>
      </ManagementPageBody>

      <ManagementDrawer
        title={employeeDrawerTitle}
        open={employeeState.drawer.open}
        onClose={() => {
          employeeState.drawer.reset();
        }}
        width={STANDARD_DRAWER_WIDTH}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: () => employeeState.drawer.reset() },
          { key: 'save', label: '保存', type: 'primary', loading: employeeSaving, onClick: () => void saveEmployee() },
        ]}
      >
        <Form layout="vertical" form={employeeForm} initialValues={{ avatarKey: DEFAULT_AVATAR_KEY, systemPrompt: employeePromptTemplate }}>
          <Tabs
            defaultActiveKey="basic"
            items={[
              {
                key: 'basic',
                label: '员工资料',
                children: (
                  <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <Row gutter={16}>
                      <Col xs={24} md={12}>
                        <Form.Item
                          label="用户名"
                          name="username"
                          rules={[
                            { required: true, message: '请输入用户名' },
                            { pattern: /^[a-z][a-zA-Z0-9-]*$/, message: '用户名需为 lowerCamelCase 或短横线格式' },
                          ]}
                        >
                          <Input placeholder="例如：aiAssistant" />
                        </Form.Item>
                      </Col>
                      <Col xs={24} md={12}>
                        <Form.Item label="昵称" name="nickname" rules={[{ required: true, message: '请输入昵称' }]}>
                          <Input placeholder="例如：小助手" />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Row gutter={16}>
                      <Col xs={24} md={12}>
                        <Form.Item label="职位" name="position">
                          <Input placeholder="例如：智能客服" />
                        </Form.Item>
                      </Col>
                      <Col xs={24} md={12}>
                        <Form.Item label="默认 LLM 服务" name="defaultLlmServiceId">
                          <Select allowClear options={llmServiceOptions} placeholder="请选择默认模型服务（可选）" />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Form.Item label="头像" name="avatarKey">
                      <Radio.Group>
                        <Space wrap>
                          {AVATAR_OPTIONS.map((option) => (
                            <Radio key={option.key} value={option.key}>
                              <Space direction="vertical" align="center" size={0}>
                                <Avatar style={{ backgroundColor: option.color }} icon={option.icon} />
                              </Space>
                            </Radio>
                          ))}
                        </Space>
                      </Radio.Group>
                    </Form.Item>
                    <Form.Item label="简介" name="description">
                      <Input.TextArea rows={3} placeholder="简单说明这个 AI 员工的职责与边界" />
                    </Form.Item>
                    <Form.Item label="问候语" name="greeting">
                      <Input.TextArea rows={2} placeholder="用户打开对话时展示的欢迎语" />
                    </Form.Item>
                  </Space>
                ),
              },
              {
                key: 'prompt',
                label: '人物设定',
                children: (
                  <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <Alert
                      type="info"
                      showIcon
                      message="AI 模型的系统提示词，决定了‘我’是谁，遵循哪些要求来工作和完成任务。"
                    />
                    <Space wrap>
                      <Button
                        icon={<MinusOutlined />}
                        onClick={() => {
                          employeeForm.setFieldValue('systemPrompt', '');
                        }}
                      >
                        清空
                      </Button>
                      <Button
                        icon={<SyncOutlined />}
                        onClick={() => {
                          employeeForm.setFieldValue('systemPrompt', employeePromptTemplate);
                        }}
                      >
                        恢复默认模板
                      </Button>
                    </Space>
                    <Form.Item name="systemPrompt" label="systemPrompt">
                      <Input.TextArea rows={12} placeholder="请输入系统提示词" />
                    </Form.Item>
                  </Space>
                ),
              },
              {
                key: 'skills',
                label: '技能',
                children: (
                  <Space direction="vertical" size={16} style={{ width: '100%' }}>
                    <Alert
                      type="info"
                      showIcon
                      message="通用技能第一期只读展示，自定义技能可设置访问 / 允许 / 禁用。"
                    />
                    <SkillSection
                      title="通用技能"
                      skills={commonSkills}
                      readOnly
                      modes={employeeSkillModes}
                      onModeChange={() => undefined}
                    />
                    <SkillSection
                      title="自定义技能"
                      skills={customSkills}
                      readOnly={false}
                      modes={employeeSkillModes}
                      onModeChange={(skillCode, mode) => {
                        setEmployeeSkillModes((prev) => ({
                          ...prev,
                          [skillCode]: mode,
                        }));
                      }}
                    />
                  </Space>
                ),
              },
            ]}
          />
        </Form>
      </ManagementDrawer>

      <ManagementDrawer
        title={llmDrawerTitle}
        open={llmState.drawer.open}
        onClose={() => {
          llmState.drawer.reset();
          setSelectedLlmService(null);
          setLlmTestResult(null);
        }}
        width={STANDARD_DRAWER_WIDTH}
        footerActions={[
          {
            key: 'test',
            label: (
              <Space size={4}>
                <SyncOutlined />
                测试连接
              </Space>
            ),
            loading: llmTesting,
            disabled: llmSaving || !actionPermission.can(['ai:llm:create', 'ai:llm:update']),
            onClick: () => void testLlmService(),
          },
          { key: 'cancel', label: '取消', onClick: () => llmState.drawer.reset() },
          { key: 'save', label: '保存', type: 'primary', loading: llmSaving, onClick: () => void saveLlmService() },
        ]}
      >
        <Form layout="vertical" form={llmForm} onValuesChange={() => setLlmTestResult(null)}>
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Row gutter={16}>
              <Col xs={24} md={12}>
                <Form.Item label="LLM 类型" name="provider" rules={[{ required: true, message: '请选择 LLM 类型' }]}>
                  <Select options={PROVIDER_OPTIONS} placeholder="请选择供应商类型" onChange={handleProviderChange} />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item label="唯一标识" name="code" rules={[{ required: true, message: '请输入唯一标识' }]}>
                  <Input placeholder="例如：default-chat" />
                </Form.Item>
              </Col>
            </Row>
            <Row gutter={16}>
              <Col xs={24} md={12}>
                <Form.Item label="标题" name="title" rules={[{ required: true, message: '请输入标题' }]}>
                  <Input placeholder="例如：默认对话模型" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item label="默认模型" name="defaultModel">
                  <Input placeholder="例如：qwen-plus / qwen-plus-latest / deepseek-v4-flash" />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item label="Base URL" name="baseUrl">
              <Input placeholder="阿里云百炼：https://dashscope.aliyuncs.com/compatible-mode/v1" />
            </Form.Item>
            <Form.Item label="API Key" name="apiKey">
              <Input.Password placeholder={selectedLlmService?.apiKeyConfigured ? '留空则使用已保存 API Key' : '请输入 API Key'} autoComplete="off" />
            </Form.Item>
            {llmTestResult ? (
              <Alert
                showIcon
                type={llmTestResult.success ? 'success' : 'error'}
                message={llmTestResult.success ? '测试通过' : '测试失败'}
                description={
                  <Space direction="vertical" size={4}>
                    <Typography.Text>{llmTestResult.message || (llmTestResult.success ? '当前 LLM 服务可正常响应' : '请检查 Base URL、模型和 API Key')}</Typography.Text>
                    {llmTestResult.success ? (
                      <Typography.Text type="secondary">
                        {[
                          llmTestResult.model ? `模型：${llmTestResult.model}` : null,
                          llmTestResult.latencyMs != null ? `耗时：${llmTestResult.latencyMs} ms` : null,
                          llmTestResult.replyText ? `响应：${llmTestResult.replyText}` : null,
                        ]
                          .filter(Boolean)
                          .join(' ｜ ')}
                      </Typography.Text>
                    ) : null}
                  </Space>
                }
              />
            ) : null}
            <Row gutter={16}>
              <Col xs={24} md={8}>
                <Form.Item label="超时时间（毫秒）" name="timeoutMs">
                  <InputNumber min={1000} step={1000} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item label="Temperature" name="temperature">
                  <InputNumber min={0} max={2} step={0.01} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item label="Max Tokens" name="maxTokens">
                  <InputNumber min={1} step={128} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item label="启用状态" name="enabled" valuePropName="checked">
              <Switch checkedChildren="启用" unCheckedChildren="禁用" />
            </Form.Item>
          </Space>
        </Form>
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default AiEmployeesPage;
