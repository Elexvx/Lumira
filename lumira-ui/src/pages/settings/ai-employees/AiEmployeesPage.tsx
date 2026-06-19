import { history, useLocation } from '@umijs/max';
import { Avatar, Card, Form, Space, Tabs, Tag, Typography, theme } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { createElement, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { ProColumns } from '@ant-design/pro-components';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import { confirmAction } from '@/utils/confirm';
import { TableActionBar, type TableActionItem } from '@/features/table/TableActionBar';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { request } from '@/services/common/request';
import { AppstoreOutlined, CustomerServiceOutlined, RobotOutlined, SafetyCertificateOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import type {
  AiEmployeeCapabilityRecord,
  AiEmployeeDetailRecord,
  AiEmployeeRecord,
  AiPromptTemplateRecord,
  AiKnowledgeBaseRecord,
  AiLlmServiceRecord,
  AiLlmServiceTestResult,
  PagedResult,
} from '@/types/api';
import { EmployeeDrawer, type EmployeeFormValues } from './components/EmployeeDrawer';
import { LlmServiceDrawer } from './components/LlmServiceDrawer';
import type { LlmFormValues } from './components/LlmServiceDrawer';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const EMPLOYEE_TAB_KEY = 'employees';
const LLM_TAB_KEY = 'llm-services';

type AiPageTabKey = 'employees' | 'llm-services';
type SimpleOption = {
  label: string;
  value: number;
};
type BuildActionItem = TableActionItem & {
  permission?: string;
};

const DEFAULT_AVATAR_KEY = 'avatar-purple-01';

type AvatarOption = {
  key: string;
  label: string;
  color: string;
  icon: ReactNode;
};

const AVATAR_OPTIONS: readonly AvatarOption[] = [
  { key: 'avatar-purple-01', label: t('主色', 'Primary'), color: 'colorPrimary', icon: null },
  { key: 'avatar-blue-01', label: t('信息', 'Info'), color: 'colorInfo', icon: null },
  { key: 'avatar-green-01', label: t('成功', 'Success'), color: 'colorSuccess', icon: null },
  { key: 'avatar-orange-01', label: t('警告', 'Warning'), color: 'colorWarning', icon: null },
  { key: 'avatar-red-01', label: t('错误', 'Error'), color: 'colorError', icon: null },
  { key: 'avatar-gray-01', label: t('中性', 'Neutral'), color: 'colorTextTertiary', icon: null },
];

const AVATAR_ICON_MAP = {
  'avatar-purple-01': createElement(RobotOutlined),
  'avatar-blue-01': createElement(UserOutlined),
  'avatar-green-01': createElement(TeamOutlined),
  'avatar-orange-01': createElement(CustomerServiceOutlined),
  'avatar-red-01': createElement(SafetyCertificateOutlined),
  'avatar-gray-01': createElement(AppstoreOutlined),
} as const;

const getAvatarOption = (options: readonly AvatarOption[], avatarKey?: string | null) => options.find((option) => option.key === avatarKey) || options[0];

type UseAiEmployeesEmployeeDrawerStateParams = {
  actionPermission: ReturnType<typeof usePagePermissionActions>['actionPermission'];
  employeePromptTemplate: string;
};

const useAiEmployeesEmployeeDrawerState = ({
  actionPermission,
  employeePromptTemplate,
}: UseAiEmployeesEmployeeDrawerStateParams) => {
  const [employeeForm] = Form.useForm<EmployeeFormValues>();
  const [employeeKnowledgeBaseIds, setEmployeeKnowledgeBaseIds] = useState<number[]>([]);
  const [employeeCapabilities, setEmployeeCapabilities] = useState<AiEmployeeCapabilityRecord[]>([]);
  const [employeeCapabilityModes, setEmployeeCapabilityModes] = useState<Record<string, AiEmployeeCapabilityRecord['permissionMode']>>({});
  const [employeeSaving, setEmployeeSaving] = useState(false);
  const employeeState = useCrudPageState<AiEmployeeRecord>();
  const canSaveEmployee = actionPermission.can(employeeState.drawer.editingId ? 'ai:employee:update' : 'ai:employee:create');
  const { token } = theme.useToken();
  const avatarOptions = useMemo<AvatarOption[]>(
    () =>
      AVATAR_OPTIONS.map((option) => ({
        ...option,
        color: token[option.color as keyof typeof token] as string,
        icon: AVATAR_ICON_MAP[option.key as keyof typeof AVATAR_ICON_MAP],
      })),
    [token],
  );
  const resolveAvatar = useCallback(
    (avatarKey?: string | null) => {
      const avatar = getAvatarOption(avatarOptions, avatarKey);
      return {
        label: avatar.label,
        color: avatar.color,
        icon: avatar.icon,
      };
    },
    [avatarOptions],
  );

  const openEmployeeCreate = useCallback(() => {
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
    setEmployeeKnowledgeBaseIds([]);
    setEmployeeCapabilities([]);
    setEmployeeCapabilityModes({});
  }, [employeeForm, employeePromptTemplate, employeeState.drawer]);

  const handleEmployeeCapabilityModeChange = useCallback((capabilityCode: string, checked: boolean, readOnly: boolean) => {
    const enabledMode = readOnly ? 'visit' : 'allow';
    setEmployeeCapabilityModes((current) => ({
      ...current,
      [capabilityCode]: checked ? enabledMode : 'deny',
    }));
  }, []);

  const openEmployeeEdit = useCallback(
    async (record: AiEmployeeRecord) => {
      employeeState.drawer.openEdit(record, record.id);
      try {
        const [detail, knowledgeBases, capabilities] = await Promise.all([
          request<AiEmployeeDetailRecord>(`/ai/employees/${record.id}`, {
            method: 'GET',
            ...API_OPTS.NO_REDIRECT,
          }),
          request<AiKnowledgeBaseRecord[]>(`/ai/employees/${record.id}/knowledge-bases`, {
            method: 'GET',
            ...API_OPTS.NO_REDIRECT,
          }),
          request<AiEmployeeCapabilityRecord[]>(`/ai/employees/${record.id}/capabilities`, {
            method: 'GET',
            ...API_OPTS.NO_REDIRECT,
          }),
        ]);
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
        setEmployeeKnowledgeBaseIds(knowledgeBases.map((item) => item.id));
        setEmployeeCapabilities(capabilities);
        setEmployeeCapabilityModes(Object.fromEntries(capabilities.map((item) => [item.capabilityCode, item.permissionMode])));
      } catch {
        employeeState.drawer.reset();
      }
    },
    [employeeForm, employeePromptTemplate, employeeState, setEmployeeCapabilities, setEmployeeCapabilityModes, setEmployeeKnowledgeBaseIds],
  );

  const saveEmployee = useCallback(async () => {
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
      };

      if (employeeState.drawer.editingId) {
        const employeeId = employeeState.drawer.editingId;
        await request<AiEmployeeRecord>(`/ai/employees/${employeeId}`, {
          method: 'PUT',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
        await Promise.all([
          request<boolean>(`/ai/employees/${employeeId}/knowledge-bases`, {
            method: 'PUT',
            data: { knowledgeBaseIds: employeeKnowledgeBaseIds },
            ...API_OPTS.NO_REDIRECT,
          }),
          request<boolean>(`/ai/employees/${employeeId}/capabilities`, {
            method: 'PUT',
            data: {
              capabilities: employeeCapabilities.map((item) => ({
                capabilityCode: item.capabilityCode,
                permissionMode: employeeCapabilityModes[item.capabilityCode] || (item.readOnly ? 'visit' : 'deny'),
              })),
            },
            ...API_OPTS.NO_REDIRECT,
          }),
        ]);
        message.success(t('数字员工已更新', 'AI employee updated'));
      } else {
        const created = await request<AiEmployeeRecord>('/ai/employees', {
          method: 'POST',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
        if (created.id) {
          await request<boolean>(`/ai/employees/${created.id}/knowledge-bases`, {
            method: 'PUT',
            data: { knowledgeBaseIds: employeeKnowledgeBaseIds },
            ...API_OPTS.NO_REDIRECT,
          });
        }
        message.success(t('数字员工已创建', 'AI employee created'));
      }

      employeeState.drawer.reset();
      employeeState.reloadTable();
    } finally {
      setEmployeeSaving(false);
    }
  }, [employeeCapabilities, employeeCapabilityModes, employeeForm, employeeKnowledgeBaseIds, employeeState, setEmployeeSaving]);

  const closeEmployeeDrawer = useCallback(() => {
    employeeState.drawer.reset();
  }, [employeeState.drawer]);

  return {
    employeeState,
    employeeForm,
    employeeKnowledgeBaseIds,
    employeeCapabilities,
    employeeCapabilityModes,
    employeeSaving,
    canSaveEmployee,
    avatarOptions,
    resolveAvatar,
    openEmployeeCreate,
    openEmployeeEdit,
    saveEmployee,
    handleEmployeeCapabilityModeChange,
    closeEmployeeDrawer,
    setEmployeeKnowledgeBaseIds,
    setEmployeeCapabilities,
    setEmployeeCapabilityModes,
    setEmployeeSaving,
  };
};

const parseTabKey = (value?: string | null): AiPageTabKey => (value === LLM_TAB_KEY ? value : EMPLOYEE_TAB_KEY);

const buildLlmServiceOptions = (services: Array<{ id: number; title: string; code: string }>): SimpleOption[] =>
  services.map((service) => ({
    label: `${service.title} (${service.code})`,
    value: service.id,
  }));

const buildKnowledgeBaseOptions = (knowledgeBases: AiKnowledgeBaseRecord[]): SimpleOption[] =>
  knowledgeBases.map((knowledgeBase) => ({
    label: `${knowledgeBase.name}${knowledgeBase.visibilityScope === 'TENANT' ? t('（企业）', ' (Tenant)') : t('（个人）', ' (Personal)')}`,
    value: knowledgeBase.id,
  }));

const buildEmployeeColumns = ({
  isDesktop,
  isMobile,
  buildTableActions,
  onEdit,
  onToggle,
  onDelete,
  resolveAvatar,
}: {
  isDesktop: boolean;
  isMobile: boolean;
  buildTableActions: (items: BuildActionItem[]) => TableActionItem[];
  onEdit: (record: AiEmployeeRecord) => void;
  onToggle: (record: AiEmployeeRecord) => void;
  onDelete: (record: AiEmployeeRecord) => void;
  resolveAvatar: (avatarKey?: string | null) => { label: string; color: string; icon: ReactNode };
}): ProColumns<AiEmployeeRecord>[] => [
  {
    title: t('头像', 'Avatar'),
    dataIndex: 'avatarKey',
    width: 'var(--saas-spacing-96)',
    render: (_, record) => {
      const avatar = resolveAvatar(record.avatarKey);
      return (
        <Space>
          <Avatar style={{ backgroundColor: avatar.color }} icon={avatar.icon} />
          <Typography.Text type="secondary">{avatar.label}</Typography.Text>
        </Space>
      );
    },
  },
  { title: t('用户名', 'Username'), dataIndex: 'username', width: 'var(--saas-spacing-180)' },
  { title: t('昵称', 'Nickname'), dataIndex: 'nickname', width: 'var(--saas-spacing-180)' },
  { title: t('职位', 'Position'), dataIndex: 'position', width: 'var(--saas-spacing-160)', ellipsis: true, render: (_, record) => record.position || '-' },
  {
    title: t('启用状态', 'Enabled status'),
    dataIndex: 'enabled',
    width: 'var(--saas-spacing-120)',
    render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{record.enabled ? t('已启用', 'Enabled') : t('已禁用', 'Disabled')}</Tag>,
  },
  {
    title: t('操作', 'Actions'),
    valueType: 'option',
    fixed: isDesktop ? 'right' : undefined,
    width: 'var(--saas-spacing-180)',
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={buildTableActions([
          { key: 'edit', label: t('编辑', 'Edit'), onClick: () => void onEdit(record), permission: 'ai:employee:update' },
          {
            key: 'toggle',
            label: record.enabled ? t('禁用', 'Disable') : t('启用', 'Enable'),
            onClick: () => void onToggle(record),
            permission: 'ai:employee:status',
          },
          { key: 'delete', label: t('删除', 'Delete'), onClick: () => onDelete(record), permission: 'ai:employee:delete', danger: true },
        ])}
      />
    ),
  },
];

const PROVIDER_OPTIONS = [
  { label: t('阿里云百炼', 'Alibaba Cloud Bailian'), value: 'aliyun-bailian' },
  { label: t('DashScope（兼容旧配置）', 'DashScope (legacy compatible)'), value: 'dashscope' },
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

const buildLlmPayload = (values: {
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
}) => ({
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

const buildLlmColumns = ({
  isDesktop,
  isMobile,
  buildTableActions,
  onEdit,
  onToggle,
  onDelete,
}: {
  isDesktop: boolean;
  isMobile: boolean;
  buildTableActions: (items: BuildActionItem[]) => TableActionItem[];
  onEdit: (record: AiLlmServiceRecord) => void;
  onToggle: (record: AiLlmServiceRecord) => void;
  onDelete: (record: AiLlmServiceRecord) => void;
}): ProColumns<AiLlmServiceRecord>[] => [
  { title: t('唯一标识', 'Code'), dataIndex: 'code', width: 'var(--saas-spacing-180)' },
  { title: t('标题', 'Title'), dataIndex: 'title', width: 'var(--saas-spacing-220)', ellipsis: true },
  { title: t('LLM 类型', 'LLM provider'), dataIndex: 'provider', width: 'var(--saas-spacing-180)' },
  { title: t('默认模型', 'Default model'), dataIndex: 'defaultModel', width: 'var(--saas-spacing-180)', render: (_, record) => record.defaultModel || '-' },
  {
    title: t('启用状态', 'Enabled status'),
    dataIndex: 'enabled',
    width: 'var(--saas-spacing-120)',
    render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{record.enabled ? t('已启用', 'Enabled') : t('已禁用', 'Disabled')}</Tag>,
  },
  {
    title: t('操作', 'Actions'),
    valueType: 'option',
    fixed: isDesktop ? 'right' : undefined,
    width: 'var(--saas-spacing-180)',
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={buildTableActions([
          { key: 'edit', label: t('编辑', 'Edit'), onClick: () => void onEdit(record), permission: 'ai:llm:update' },
          {
            key: 'toggle',
            label: record.enabled ? t('禁用', 'Disable') : t('启用', 'Enable'),
            onClick: () => void onToggle(record),
            permission: 'ai:llm:status',
          },
          { key: 'delete', label: t('删除', 'Delete'), onClick: () => onDelete(record), permission: 'ai:llm:delete', danger: true },
        ])}
      />
    ),
  },
];

const AiEmployeesPage = () => {
  const { actionPermission, responsive, buildToolbarButtons } = usePagePermissionActions();
  const [employeePromptTemplate, setEmployeePromptTemplate] = useState('');
  const [llmServiceOptions, setLlmServiceOptions] = useState<SimpleOption[]>([]);
  const [knowledgeBaseOptions, setKnowledgeBaseOptions] = useState<SimpleOption[]>([]);
  const [bootstrapLoading, setBootstrapLoading] = useState(true);
  const employeeContext = useAiEmployeesEmployeeDrawerState({
    employeePromptTemplate,
    actionPermission,
  });
  const {
    employeeState,
    employeeForm,
    employeeKnowledgeBaseIds,
    employeeCapabilities,
    employeeCapabilityModes,
    employeeSaving,
    canSaveEmployee,
    avatarOptions,
    resolveAvatar,
    openEmployeeCreate,
    openEmployeeEdit,
    saveEmployee,
    handleEmployeeCapabilityModeChange,
    closeEmployeeDrawer,
    setEmployeeKnowledgeBaseIds,
  } = employeeContext;

  const [llmForm] = Form.useForm<LlmFormValues>();
  const [selectedLlmService, setSelectedLlmService] = useState<AiLlmServiceRecord | null>(null);
  const [llmTestResult, setLlmTestResult] = useState<AiLlmServiceTestResult | null>(null);
  const llmState = useCrudPageState<AiLlmServiceRecord>();
  const {
    actionRef: llmActionRef,
    drawer: llmDrawer,
    reloadTable: reloadLlmTable,
  } = llmState;
  const [llmSaving, setLlmSaving] = useState(false);
  const [llmTesting, setLlmTesting] = useState(false);

  const reloadLlmServiceOptions = useCallback(async () => {
    try {
      const services = await request<PagedResult<AiLlmServiceRecord>>('/ai/llm-services', {
        method: 'GET',
        params: { pageNo: 1, pageSize: 200 },
        ...API_OPTS.NO_REDIRECT,
      });
      setLlmServiceOptions(buildLlmServiceOptions(services.records || []));
    } catch (error) {
      showErrorMessage(error, t('刷新 LLM 服务选项失败', 'Failed to refresh LLM service options'));
    }
  }, []);

  const openLlmCreate = useCallback(() => {
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
  }, [llmForm, llmState.drawer]);

  const openLlmEdit = useCallback(
    async (record: AiLlmServiceRecord) => {
      llmState.drawer.openEdit(record, record.id);
      setLlmTestResult(null);
      try {
        const detail = await request<AiLlmServiceRecord>(`/ai/llm-services/${record.id}`, {
          method: 'GET',
          ...API_OPTS.NO_REDIRECT,
        });
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
    },
    [llmForm, llmState],
  );

  const closeLlmDrawer = useCallback(() => {
    llmState.drawer.reset();
    setSelectedLlmService(null);
    setLlmTestResult(null);
  }, [llmState.drawer]);

  const clearLlmTestResult = useCallback(() => {
    setLlmTestResult(null);
  }, []);

  const handleProviderChange = useCallback(
    (provider: string) => {
      const defaults = PROVIDER_DEFAULTS[provider];
      if (!defaults) {
        return;
      }
      setLlmTestResult(null);
      llmForm.setFieldsValue({
        baseUrl: defaults.baseUrl,
        defaultModel: defaults.defaultModel || llmForm.getFieldValue('defaultModel'),
      });
    },
    [llmForm],
  );

  const saveLlmService = useCallback(async () => {
    setLlmSaving(true);
    try {
      const values = await llmForm.validateFields();
      const payload = buildLlmPayload(values);

      if (llmState.drawer.editingId) {
        await request<AiLlmServiceRecord>(`/ai/llm-services/${llmState.drawer.editingId}`, {
          method: 'PUT',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('LLM 服务已更新', 'LLM service updated'));
      } else {
        await request<AiLlmServiceRecord>('/ai/llm-services', {
          method: 'POST',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('LLM 服务已创建', 'LLM service created'));
      }

      closeLlmDrawer();
      void reloadLlmTable();
      await reloadLlmServiceOptions();
    } finally {
      setLlmSaving(false);
    }
  }, [closeLlmDrawer, llmForm, llmState.drawer.editingId, reloadLlmServiceOptions, reloadLlmTable]);

  const testLlmService = useCallback(async () => {
    setLlmTesting(true);
    setLlmTestResult(null);
    try {
      await llmForm.validateFields(['provider']);
      const values = llmForm.getFieldsValue();
      const payload = buildLlmPayload(values);
      const result = await request<AiLlmServiceTestResult>('/ai/llm-services/test', {
        method: 'POST',
        data: {
          ...payload,
          serviceId: llmState.drawer.editingId || selectedLlmService?.id || null,
        },
        ...API_OPTS.SILENT_NO_REDIRECT,
      });
      setLlmTestResult(result);
      if (result.success) {
        message.success(t('LLM 服务测试通过', 'LLM service test passed'));
      } else {
        message.warning(result.message || t('LLM 服务测试失败', 'LLM service test failed'));
      }
    } catch (error) {
      const errorMessage = error instanceof Error && error.message ? error.message : t('LLM 服务测试失败', 'LLM service test failed');
      setLlmTestResult({ success: false, message: errorMessage });
      message.error(errorMessage);
    } finally {
      setLlmTesting(false);
    }
  }, [llmForm, llmState.drawer.editingId, selectedLlmService?.id]);

  const canSaveLlmService = actionPermission.can(llmDrawer.editingId ? 'ai:llm:update' : 'ai:llm:create');
  const canRunLlmTest = actionPermission.can(['ai:llm:create', 'ai:llm:update']);
  const toggleLlmServiceEnabled = useCallback(
    (record: AiLlmServiceRecord) => {
      const nextEnabled = !record.enabled;
      if (!nextEnabled) {
        confirmAction({
          title: t('禁用 LLM 服务', 'Disable LLM service'),
          content: t(`确认禁用 LLM 服务「${record.title}」吗？`, `Disable LLM service "${record.title}"?`),
          okText: t('确认禁用', 'Disable'),
          okButtonProps: { danger: true },
          onOk: async () => {
            await request<boolean>(`/ai/llm-services/${record.id}/enabled`, {
              method: 'PATCH',
              data: { enabled: false },
              ...API_OPTS.NO_REDIRECT,
            });
            reloadLlmTable();
          },
        });
        return;
      }

      void (async () => {
        await request<boolean>(`/ai/llm-services/${record.id}/enabled`, {
          method: 'PATCH',
          data: { enabled: true },
          ...API_OPTS.NO_REDIRECT,
        });
        reloadLlmTable();
      })();
    },
    [reloadLlmTable],
  );
  const deleteLlmService = useCallback(
    (record: AiLlmServiceRecord) => {
      confirmAction({
        title: t('删除 LLM 服务', 'Delete LLM service'),
        content: t(`确认删除 LLM 服务「${record.title}」吗？`, `Delete LLM service "${record.title}"?`),
        okText: t('确认删除', 'Delete'),
        okButtonProps: { danger: true },
        onOk: async () => {
          await request<boolean>(`/ai/llm-services/${record.id}`, {
            method: 'DELETE',
            ...API_OPTS.NO_REDIRECT,
          });
          message.success(t('LLM 服务已删除', 'LLM service deleted'));
          reloadLlmTable();
          await reloadLlmServiceOptions();
        },
      });
    },
    [reloadLlmServiceOptions, reloadLlmTable],
  );
  const llmColumns = useMemo(
    () =>
      buildLlmColumns({
        isDesktop: responsive.isDesktop,
        isMobile: responsive.isMobile,
        buildTableActions: actionPermission.buildTableActions,
        onEdit: openLlmEdit,
        onToggle: toggleLlmServiceEnabled,
        onDelete: deleteLlmService,
      }),
    [actionPermission, deleteLlmService, openLlmEdit, responsive.isDesktop, responsive.isMobile, toggleLlmServiceEnabled],
  );
  const llmToolbarButtons = buildToolbarButtons([
    {
      key: 'create-llm',
      label: t('新增 LLM 服务', 'Create LLM service'),
      type: 'primary',
      onClick: openLlmCreate,
      permission: 'ai:llm:create',
    },
    {
      key: 'refresh-llm',
      label: t('刷新', 'Refresh'),
      onClick: () => void reloadLlmTable(),
    },
  ]);
  const llmTabProps = {
    actionRef: llmActionRef,
    columns: llmColumns,
    isMobile: responsive.isMobile,
    loading: bootstrapLoading,
    request: (params: { current?: number; pageSize?: number }) =>
      request<PagedResult<AiLlmServiceRecord>>('/ai/llm-services', {
        method: 'GET',
        params,
        ...API_OPTS.NO_REDIRECT,
      }),
  };
  const llmDrawerProps = {
    open: llmDrawer.open,
    title: llmDrawer.editingId ? t('编辑 LLM 服务', 'Edit LLM service') : t('新建 LLM 服务', 'Create LLM service'),
    form: llmForm,
    selectedService: selectedLlmService,
    llmTestResult,
    llmTesting,
    llmSaving,
    canSaveLlmService,
    canRunTest: canRunLlmTest,
    providerOptions: PROVIDER_OPTIONS,
    onClose: closeLlmDrawer,
    onProviderChange: handleProviderChange,
    onSave: () => void saveLlmService(),
    onTest: () => void testLlmService(),
    onValuesChange: clearLlmTestResult,
  };

  useEffect(() => {
    let active = true;

    const loadBootstrapData = async () => {
      setBootstrapLoading(true);
      try {
        const [template, services, knowledgeBases] = await Promise.all([
          request<AiPromptTemplateRecord>('/ai/employees/template', {
            method: 'GET',
            ...API_OPTS.NO_REDIRECT,
          }),
          request<PagedResult<AiLlmServiceRecord>>('/ai/llm-services', {
            method: 'GET',
            params: { pageNo: 1, pageSize: 200 },
            ...API_OPTS.NO_REDIRECT,
          }),
          request<PagedResult<AiKnowledgeBaseRecord>>('/ai/knowledge-bases', {
            method: 'GET',
            params: { pageNo: 1, pageSize: 200, status: 'ENABLED' },
            ...API_OPTS.NO_REDIRECT,
          }),
        ]);

        if (!active) {
          return;
        }

        setEmployeePromptTemplate(template.defaultSystemPromptTemplate);
        setLlmServiceOptions(buildLlmServiceOptions(services.records || []));
        setKnowledgeBaseOptions(buildKnowledgeBaseOptions(knowledgeBases.records || []));
      } catch (error) {
        if (active) {
          showErrorMessage(error, t('加载数字员工基础数据失败', 'Failed to load AI employee base data'));
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

  const location = useLocation();
  const searchParams = useMemo(() => new URLSearchParams(location.search), [location.search]);
  const activeTab = parseTabKey(searchParams.get('tab'));
  const syncTab = useCallback(
    (tab: AiPageTabKey) => {
      const nextSearch = new URLSearchParams(location.search);
      nextSearch.set('tab', tab);
      history.replace(`${location.pathname}?${nextSearch.toString()}`);
    },
    [location.pathname, location.search],
  );

  const toggleEmployeeEnabled = useCallback(
    (record: AiEmployeeRecord) => {
      const nextEnabled = !record.enabled;
      if (!nextEnabled) {
        confirmAction({
          title: t('禁用数字员工', 'Disable AI employee'),
          content: t(`确认禁用数字员工「${record.nickname || record.username}」吗？`, `Disable AI employee "${record.nickname || record.username}"?`),
          okText: t('确认禁用', 'Disable'),
          okButtonProps: { danger: true },
          onOk: async () => {
            await request<boolean>(`/ai/employees/${record.id}/enabled`, {
              method: 'PATCH',
              data: { enabled: false },
              ...API_OPTS.NO_REDIRECT,
            });
            message.success(t('状态已更新', 'Status updated'));
            employeeState.reloadTable();
          },
        });
        return;
      }

      void (async () => {
        await request<boolean>(`/ai/employees/${record.id}/enabled`, {
          method: 'PATCH',
          data: { enabled: true },
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('状态已更新', 'Status updated'));
        employeeState.reloadTable();
      })();
    },
    [employeeState],
  );

  const deleteEmployee = useCallback(
    (record: AiEmployeeRecord) => {
      confirmAction({
        title: t('删除数字员工', 'Delete AI employee'),
        content: t(`确认删除数字员工「${record.nickname || record.username}」吗？`, `Delete AI employee "${record.nickname || record.username}"?`),
        okText: t('确认删除', 'Delete'),
        okButtonProps: { danger: true },
        onOk: async () => {
          await request<boolean>(`/ai/employees/${record.id}`, {
            method: 'DELETE',
            ...API_OPTS.NO_REDIRECT,
          });
          message.success(t('数字员工已删除', 'AI employee deleted'));
          employeeState.reloadTable();
        },
      });
    },
    [employeeState],
  );

  const employeeColumns = useMemo(
    () =>
      buildEmployeeColumns({
        isDesktop: responsive.isDesktop,
        isMobile: responsive.isMobile,
        buildTableActions: actionPermission.buildTableActions,
        onEdit: openEmployeeEdit,
        onToggle: toggleEmployeeEnabled,
        onDelete: deleteEmployee,
        resolveAvatar,
      }),
    [actionPermission, deleteEmployee, openEmployeeEdit, resolveAvatar, responsive.isDesktop, responsive.isMobile, toggleEmployeeEnabled],
  );

  const employeeToolbarButtons = buildToolbarButtons([
    {
      key: 'create-employee',
      label: t('新建 AI 员工', 'Create AI employee'),
      type: 'primary',
      onClick: openEmployeeCreate,
      permission: 'ai:employee:create',
    },
    {
      key: 'refresh-employee',
      label: t('刷新', 'Refresh'),
      onClick: employeeState.reloadTable,
    },
  ]);

  const tabBarExtraContent = activeTab === EMPLOYEE_TAB_KEY ? employeeToolbarButtons : llmToolbarButtons;
  const cardPaddingTop = resolveResponsiveValue(APP_SPACING.tagWrapGap, responsive.isMobile)[0];

  const employeeTabProps = {
    actionRef: employeeState.actionRef,
    columns: employeeColumns,
    isMobile: responsive.isMobile,
    loading: bootstrapLoading,
    request: (params: { current?: number; pageSize?: number } = {}) =>
      request<PagedResult<AiEmployeeRecord>>('/ai/employees', {
        method: 'GET',
        params,
        ...API_OPTS.NO_REDIRECT,
      }),
  };
  const employeeDrawerProps = {
    open: employeeState.drawer.open,
    title: employeeState.drawer.editingId ? t('编辑 AI 员工', 'Edit AI employee') : t('新建 AI 员工', 'Create AI employee'),
    form: employeeForm,
    employeePromptTemplate,
    avatarOptions,
    llmServiceOptions,
    knowledgeBaseOptions,
    employeeKnowledgeBaseIds,
    employeeCapabilities,
    employeeCapabilityModes,
    editingId: employeeState.drawer.editingId,
    saving: employeeSaving,
    canSave: canSaveEmployee,
    onClose: closeEmployeeDrawer,
    onSave: () => void saveEmployee(),
    onKnowledgeBaseIdsChange: (values: number[]) => setEmployeeKnowledgeBaseIds(values),
    onCapabilityModeChange: handleEmployeeCapabilityModeChange,
  };

  return (
    <ManagementPage className="saas-crud-page" ghost title={t('数字员工', 'AI employees')} content={null}>
      <ManagementPageBody>
        <Card className="saas-ai-employees-card" bodyStyle={{ paddingTop: cardPaddingTop }}>
          <Tabs
            activeKey={activeTab}
            destroyInactiveTabPane={false}
            onChange={(key) => syncTab(key as typeof activeTab)}
            tabBarExtraContent={<Space wrap>{tabBarExtraContent}</Space>}
            items={[
              {
                key: EMPLOYEE_TAB_KEY,
                label: t('AI 员工', 'AI employees'),
                children: (
                  <ManagementTable<AiEmployeeRecord>
                    {...employeeTabProps}
                    rowKey="id"
                    search={false}
                    toolBarRender={false}
                    request={buildTableRequest(employeeTabProps.request)}
                  />
                ),
              },
              {
                key: LLM_TAB_KEY,
                label: t('LLM 服务', 'LLM services'),
                children: (
                  <ManagementTable<AiLlmServiceRecord>
                    {...llmTabProps}
                    rowKey="id"
                    search={false}
                    toolBarRender={false}
                    request={buildTableRequest(llmTabProps.request)}
                  />
                ),
              },
            ]}
          />
        </Card>
      </ManagementPageBody>

      <EmployeeDrawer {...employeeDrawerProps} />
      <LlmServiceDrawer {...llmDrawerProps} />
    </ManagementPage>
  );
};

export default AiEmployeesPage;
