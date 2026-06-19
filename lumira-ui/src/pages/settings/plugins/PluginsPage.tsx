import { formatMessage, history } from '@umijs/max';
import { BuildOutlined, CloudUploadOutlined, DeleteOutlined, FileSearchOutlined, SyncOutlined } from '@ant-design/icons';
import { Button, Card, Col, Descriptions, Empty, Input, Modal, Radio, Row, Space, Switch, Tag, Typography, Upload, theme } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import type { DescriptionsProps } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useInitialStateModel } from '@/hooks/useInitialStateModel';
import { useDetailDescriptionsProps } from '@/features/detail/config';
import { ManagementDrawer } from '@/features/management/ManagementDrawer';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { TableActionBar } from '@/features/table/TableActionBar';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { ApiRequestError } from '@/services/common/requestInternalsTypes';
import { request, type RequestOptions } from '@/services/common/request';
import type { MenuNode, PluginDefinition, PluginRuntimeLog, PluginVersion, TenantPlugin } from '@/types/api';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import { confirmAction } from '@/utils/confirm';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const resolvePluginManagementPath = (pluginCode: string) => {
  if (pluginCode === 'sensitive-words') {
    return '/plugins/sensitive-words';
  }
  return null;
};

const PluginCardsGrid = ({
  isMobile,
  loading,
  definitions,
  currentAvailableMap,
  getPreferredEnableVersion,
  mutationLoading,
  canEnable,
  canDisable,
  canViewLogs,
  onToggleEnable,
  onOpenDetails,
  onOpenVersions,
  onOpenLogs,
  onUninstall,
}: {
  isMobile: boolean;
  loading: boolean;
  definitions: PluginDefinition[];
  currentAvailableMap: Map<string, TenantPlugin>;
  getPreferredEnableVersion: (pluginCode: string) => { version: string } | undefined;
  mutationLoading: boolean;
  canEnable: boolean;
  canDisable: boolean;
  canViewLogs: boolean;
  onToggleEnable: (pluginCode: string, enabled: boolean, versionLabel?: string) => void;
  onOpenDetails: (plugin: PluginDefinition) => void;
  onOpenVersions: (plugin: PluginDefinition) => void;
  onOpenLogs: (plugin: PluginDefinition) => void;
  onUninstall: (plugin: PluginDefinition) => void;
}) => {
  const rowGutter = resolveResponsiveValue(APP_SPACING.rowGutterPanel, isMobile);
  const sectionGap = resolveResponsiveValue(APP_SPACING.sectionGap, isMobile);

  if (!loading && !definitions.length) {
    return (
      <div style={{ minHeight: 'var(--saas-spacing-240)', display: 'grid', placeItems: 'center' }}>
        <Empty description={t('暂无插件定义', 'No plugin definitions')} />
      </div>
    );
  }

  return (
    <Row gutter={rowGutter}>
      {definitions.map((plugin) => {
        const preferredEnableVersion = getPreferredEnableVersion(plugin.pluginCode);
        const enabledPlugin = currentAvailableMap.get(plugin.pluginCode);
        const enabled = Boolean(enabledPlugin);
        const versionLabel = enabledPlugin?.version || preferredEnableVersion?.version;
        const canToggle = enabled ? canDisable : canEnable;
        const managementPath = enabled ? resolvePluginManagementPath(plugin.pluginCode) : null;

        return (
          <Col key={plugin.pluginCode} xs={24} lg={12} xxl={8}>
            <Card
              loading={loading}
              style={{ height: '100%' }}
              bodyStyle={{ height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}
              title={
                <Space wrap>
                  <BuildOutlined />
                  <span>{plugin.pluginName}</span>
                  <Tag color={enabled ? 'green' : 'default'}>{enabled ? t('已启用', 'Enabled') : t('未启用', 'Disabled')}</Tag>
                </Space>
              }
              extra={
                <Switch
                  checked={enabled}
                  disabled={mutationLoading || !versionLabel || !canToggle}
                  onChange={(checked) => onToggleEnable(plugin.pluginCode, checked, versionLabel)}
                />
              }
            >
              <Space direction="vertical" size={sectionGap} style={{ width: '100%' }}>
                <Typography.Paragraph style={{ marginBottom: 0 }}>{plugin.description || t('暂无插件描述', 'No plugin description')}</Typography.Paragraph>
                <Space wrap>
                  {managementPath ? (
                    <Button type="primary" onClick={() => history.push(managementPath)}>
                      {t('管理', 'Manage')}
                    </Button>
                  ) : null}
                  <Button onClick={() => onOpenDetails(plugin)}>{t('详情', 'Details')}</Button>
                  <Button onClick={() => onOpenVersions(plugin)}>{t('版本', 'Versions')}</Button>
                  <Button disabled={!canViewLogs} onClick={() => onOpenLogs(plugin)} icon={<FileSearchOutlined />}>
                    {t('日志', 'Logs')}
                  </Button>
                  <Button danger disabled={!canDisable || plugin.builtinFlag === 1} icon={<DeleteOutlined />} onClick={() => onUninstall(plugin)}>
                    {t('卸载', 'Uninstall')}
                  </Button>
                </Space>
              </Space>
            </Card>
          </Col>
        );
      })}
    </Row>
  );
};

const usePluginsPageData = () => {
  const { setInitialState } = useInitialStateModel();
  const [definitions, setDefinitions] = useState<PluginDefinition[]>([]);
  const [availablePlugins, setAvailablePlugins] = useState<TenantPlugin[]>([]);
  const [versionMap, setVersionMap] = useState<Record<string, PluginVersion[]>>({});
  const [loading, setLoading] = useState(true);
  const [searchKeyword, setSearchKeyword] = useState('');

  const handlePluginPageError = useCallback((error: unknown, fallbackMessage: string) => {
    if (error instanceof ApiRequestError) {
      return;
    }
    showErrorMessage(error, fallbackMessage);
  }, []);

  const loadOverview = useCallback(async () => {
    setLoading(true);
    try {
      const [definitionList, tenantPlugins, versionResult] = await Promise.all([
        request<PluginDefinition[]>('/v1/plugins/definitions', {
          method: 'GET',
          ...API_OPTS.NO_REDIRECT,
        }),
        request<TenantPlugin[]>('/v1/plugins/current/available', {
          method: 'GET',
          ...API_OPTS.NO_REDIRECT,
        }),
        request<Record<string, PluginVersion[]>>('/v1/plugins/versions', {
          method: 'GET',
          ...API_OPTS.NO_REDIRECT,
        }),
      ]);
      const nextVersionMap: Record<string, PluginVersion[]> = { ...versionResult };
      definitionList.forEach((plugin) => {
        nextVersionMap[plugin.pluginCode] = nextVersionMap[plugin.pluginCode] || [];
      });

      setDefinitions(definitionList);
      setAvailablePlugins(tenantPlugins);
      setVersionMap(nextVersionMap);

      setInitialState((prev) =>
        prev
          ? {
              ...prev,
              availablePlugins: tenantPlugins,
            }
          : prev,
      );
    } catch (error) {
      handlePluginPageError(
        error,
        formatMessage({ id: 'page.plugins.error.load', defaultMessage: 'Failed to load plugin information, please try again later' }),
      );
    } finally {
      setLoading(false);
    }
  }, [handlePluginPageError, setInitialState]);

  useEffect(() => {
    void loadOverview();
  }, [loadOverview]);

  return {
    pluginPageDataPack: {
      definitions,
      availablePlugins,
      versionMap,
      loading,
      searchKeyword,
      setSearchKeyword,
      loadOverview,
    },
  };
};

const isInstalledVersion = (installStatus?: string) => (installStatus || '').toUpperCase() === 'INSTALLED';

const getPreferredEnableVersion = (pluginCode: string, versionMap: Record<string, PluginVersion[]>) => {
  const versions = versionMap[pluginCode] || [];
  return (
    versions.find((item) => isInstalledVersion(item.installStatus) && item.isActive === 1) ||
    versions.find((item) => isInstalledVersion(item.installStatus)) ||
    versions.find((item) => item.isActive === 1)
  );
};

const buildAvailablePluginMap = (availablePlugins: TenantPlugin[]) =>
  new Map(availablePlugins.map((item) => [item.pluginCode, item]));

const filterPluginDefinitions = (definitions: PluginDefinition[], keyword: string) => {
  const normalizedKeyword = keyword.trim().toLowerCase();
  return definitions.filter((item) => {
    if (!normalizedKeyword) {
      return true;
    }
    return item.pluginName.toLowerCase().includes(normalizedKeyword) || item.pluginCode.toLowerCase().includes(normalizedKeyword);
  });
};

const runtimeLogs = (pluginCode: string, options: RequestOptions = {}) =>
  request<PluginRuntimeLog[]>(`/v1/plugins/${pluginCode}/logs`, {
    method: 'GET',
    ...options,
  });

const buildVersionColumns = ({
  isDesktop,
  isMobile,
  canInstall,
  canUpgrade,
  canRollback,
  canDisable,
  onInstall,
  onActivate,
  onDisable,
  onRollback,
}: {
  isDesktop: boolean;
  isMobile: boolean;
  canInstall: boolean;
  canUpgrade: boolean;
  canRollback: boolean;
  canDisable: boolean;
  onInstall: (pluginCode: string, version: string) => void;
  onActivate: (pluginCode: string, version: string) => void;
  onDisable: (pluginCode: string) => void;
  onRollback: (pluginCode: string, version: string) => void;
}): ProColumns<PluginVersion>[] => [
  { title: t('版本', 'Version'), dataIndex: 'version' },
  { title: t('安装状态', 'Install status'), dataIndex: 'installStatus' },
  { title: t('加载状态', 'Load status'), dataIndex: 'loadStatus' },
  { title: t('健康状态', 'Health status'), dataIndex: 'healthStatus' },
  {
    title: t('激活', 'Active'),
    dataIndex: 'isActive',
    render: (_, record) => <Tag color={record.isActive === 1 ? 'green' : 'default'}>{record.isActive === 1 ? t('是', 'Yes') : t('否', 'No')}</Tag>,
  },
  {
    title: t('操作', 'Actions'),
    fixed: isDesktop ? 'right' : undefined,
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={[
          { key: 'install', label: t('安装', 'Install'), disabled: !canInstall, onClick: () => onInstall(record.pluginCode, record.version) },
          { key: 'activate', label: t('激活', 'Activate'), disabled: !canUpgrade, onClick: () => onActivate(record.pluginCode, record.version) },
          { key: 'disable', label: t('停用', 'Disable'), disabled: !canDisable, onClick: () => onDisable(record.pluginCode), danger: true },
          { key: 'rollback', label: t('回滚', 'Rollback'), disabled: !canRollback, onClick: () => onRollback(record.pluginCode, record.version) },
        ]}
      />
    ),
  },
];

export type PluginPanelState = {
  selectedPlugin: PluginDefinition | null;
  setSelectedPlugin: (value: PluginDefinition | null | ((current: PluginDefinition | null) => PluginDefinition | null)) => void;
  versionDrawerOpen: boolean;
  setVersionDrawerOpen: (value: boolean | ((current: boolean) => boolean)) => void;
  logDrawerOpen: boolean;
  setLogDrawerOpen: (value: boolean | ((current: boolean) => boolean)) => void;
  detailDrawerOpen: boolean;
  setDetailDrawerOpen: (value: boolean | ((current: boolean) => boolean)) => void;
  uploadVisible: boolean;
  setUploadVisible: (value: boolean | ((current: boolean) => boolean)) => void;
  uploadFile: File | null;
  setUploadFile: (value: File | null | ((current: File | null) => File | null)) => void;
  runtimeLogs: PluginRuntimeLog[];
  setRuntimeLogs: (value: PluginRuntimeLog[] | ((current: PluginRuntimeLog[]) => PluginRuntimeLog[])) => void;
  logsLoading: boolean;
  setLogsLoading: (value: boolean | ((current: boolean) => boolean)) => void;
  mutationLoading: boolean;
  setMutationLoading: (value: boolean | ((current: boolean) => boolean)) => void;
  uninstallDialogOpen: boolean;
  setUninstallDialogOpen: (value: boolean | ((current: boolean) => boolean)) => void;
  uninstallTarget: PluginDefinition | null;
  setUninstallTarget: (value: PluginDefinition | null | ((current: PluginDefinition | null) => PluginDefinition | null)) => void;
  removePluginData: boolean;
  setRemovePluginData: (value: boolean | ((current: boolean) => boolean)) => void;
  disableDialogOpen: boolean;
  setDisableDialogOpen: (value: boolean | ((current: boolean) => boolean)) => void;
  disableTarget: PluginDefinition | null;
  setDisableTarget: (value: PluginDefinition | null | ((current: PluginDefinition | null) => PluginDefinition | null)) => void;
  purgePluginDataOnDisable: boolean;
  setPurgePluginDataOnDisable: (value: boolean | ((current: boolean) => boolean)) => void;
};

type UsePluginMutationActionsParams = {
  definitions: PluginDefinition[];
  versionMap: Record<string, PluginVersion[]>;
  loadOverview: () => Promise<void>;
  panel: PluginPanelState;
};

const usePluginMutationActions = ({ definitions, versionMap, loadOverview, panel }: UsePluginMutationActionsParams) => {
  const { setInitialState } = useInitialStateModel();

  const handlePluginPageError = useCallback((error: unknown, fallbackMessage: string) => {
    if (error instanceof ApiRequestError) {
      return;
    }
    showErrorMessage(error, fallbackMessage);
  }, []);

  const refreshBootstrap = useCallback(async () => {
    const [menuTree, available] = await Promise.all([
      request<MenuNode[]>('/v1/plugins/current/menus', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
      request<TenantPlugin[]>('/v1/plugins/current/available', {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      }),
    ]);
    setInitialState((prev) =>
      prev
        ? {
            ...prev,
            menuTree,
            menuVersion: (prev.menuVersion ?? 0) + 1,
            availablePlugins: available,
            securitySettings: prev.securitySettings,
          }
        : prev,
    );
  }, [setInitialState]);

  const refreshAfterMutation = useCallback(async () => {
    try {
      await loadOverview();
    } catch {
      message.warning(
        formatMessage({
          id: 'page.plugins.error.listRefresh',
          defaultMessage: 'Plugins were updated, but the list failed to refresh. Please refresh the page manually.',
        }),
      );
    }
    try {
      await refreshBootstrap();
    } catch {
      message.warning(
        formatMessage({
          id: 'page.plugins.error.menuRefresh',
          defaultMessage: 'Plugins were updated, but the menu failed to refresh. Please refresh the page manually.',
        }),
      );
    }
  }, [loadOverview, refreshBootstrap]);

  const confirmMutation = useCallback(
    (title: string, content: string, action: () => Promise<unknown>) =>
      confirmAction({
        title,
        content,
        okText: formatMessage({ id: 'page.plugins.confirm', defaultMessage: 'Confirm' }),
        cancelText: formatMessage({ id: 'page.plugins.cancel', defaultMessage: 'Cancel' }),
        onOk: async () => {
          try {
            await action();
          } catch (error) {
            handlePluginPageError(error, formatMessage({ id: 'page.plugins.error.operation', defaultMessage: 'Operation failed, please try again later' }));
          }
        },
      }),
    [handlePluginPageError],
  );

  const runVersionMutation = useCallback(
    async (action: () => Promise<unknown>, loadingSetter: (next: boolean) => void, successMessage: string, errorMessage: string) => {
      loadingSetter(true);
      try {
        await action();
        message.success(successMessage);
        await refreshAfterMutation();
      } catch (error) {
        handlePluginPageError(error, errorMessage);
      } finally {
        loadingSetter(false);
      }
    },
    [handlePluginPageError, refreshAfterMutation],
  );

  const handleInstall = useCallback(
    async (pluginCode: string, version: string) => {
      await confirmMutation(
        formatMessage({ id: 'page.plugins.installVersion', defaultMessage: 'Install plugin version' }),
        `${pluginCode} @ ${version}`,
        async () =>
          runVersionMutation(
            () =>
              request<PluginVersion>('/v1/plugins/install', {
                method: 'POST',
                data: { pluginCode, version },
                ...API_OPTS.NO_REDIRECT,
              }),
            panel.setMutationLoading,
            formatMessage({ id: 'page.plugins.success.installed', defaultMessage: 'Plugin installed successfully' }),
            formatMessage({ id: 'page.plugins.error.install', defaultMessage: 'Failed to install plugin, please try again later' }),
          ),
      );
    },
    [confirmMutation, panel, runVersionMutation],
  );
  const handleActivate = useCallback(
    async (pluginCode: string, version: string) => {
      await confirmMutation(
        formatMessage({ id: 'page.plugins.activateVersion', defaultMessage: 'Activate plugin version' }),
        `${pluginCode} @ ${version}`,
        async () =>
          runVersionMutation(
            () =>
              request<PluginVersion>('/v1/plugins/upgrade', {
                method: 'POST',
                data: { pluginCode, version },
                ...API_OPTS.NO_REDIRECT,
              }),
            panel.setMutationLoading,
            formatMessage({ id: 'page.plugins.success.activated', defaultMessage: 'Plugin active version switched' }),
            formatMessage({ id: 'page.plugins.error.activate', defaultMessage: 'Failed to activate plugin, please try again later' }),
          ),
      );
    },
    [confirmMutation, panel, runVersionMutation],
  );
  const handleEnable = useCallback(
    async (pluginCode: string, version?: string) => {
      const versionToUse = version || getPreferredEnableVersion(pluginCode, versionMap)?.version;
      if (!versionToUse) {
        message.warning(formatMessage({ id: 'page.plugins.error.installableVersion', defaultMessage: 'Please install an available version first' }));
        panel.setSelectedPlugin(definitions.find((item) => item.pluginCode === pluginCode) || null);
        panel.setVersionDrawerOpen(true);
        return;
      }
      await confirmMutation(
        formatMessage({ id: 'page.plugins.enable', defaultMessage: 'Enable plugin' }),
        `${pluginCode} @ ${versionToUse}`,
        async () =>
          runVersionMutation(
            () =>
              request<boolean>(`/v1/plugins/${pluginCode}/enable`, {
                method: 'POST',
                data: { version: versionToUse },
                ...API_OPTS.NO_REDIRECT,
              }),
            panel.setMutationLoading,
            formatMessage({ id: 'page.plugins.success.enabled', defaultMessage: 'Plugin enabled' }),
            formatMessage({ id: 'page.plugins.error.enable', defaultMessage: 'Failed to enable plugin, please try again later' }),
          ),
      );
    },
    [confirmMutation, definitions, panel, runVersionMutation, versionMap],
  );
  const handleDisable = useCallback(
    async (pluginCode: string) => {
      panel.setDisableTarget(definitions.find((item) => item.pluginCode === pluginCode) || null);
      panel.setPurgePluginDataOnDisable(false);
      panel.setDisableDialogOpen(true);
    },
    [definitions, panel],
  );
  const handleRollback = useCallback(
    async (pluginCode: string, version: string) => {
      await confirmMutation(
        formatMessage({ id: 'page.plugins.rollbackVersion', defaultMessage: 'Rollback plugin version' }),
        `${pluginCode} -> ${version}`,
        async () =>
          runVersionMutation(
            () =>
              request<PluginVersion>('/v1/plugins/rollback', {
                method: 'POST',
                data: { pluginCode, targetVersion: version },
                ...API_OPTS.NO_REDIRECT,
              }),
            panel.setMutationLoading,
            formatMessage({ id: 'page.plugins.success.rollback', defaultMessage: 'Plugin rolled back' }),
            formatMessage({ id: 'page.plugins.error.rollback', defaultMessage: 'Failed to rollback plugin, please try again later' }),
          ),
      );
    },
    [confirmMutation, panel, runVersionMutation],
  );

  const handleUninstall = useCallback((plugin: PluginDefinition) => {
    panel.setUninstallTarget(plugin);
    panel.setRemovePluginData(false);
    panel.setUninstallDialogOpen(true);
  }, [panel]);

  const confirmUninstall = useCallback(async () => {
    if (!panel.uninstallTarget) {
      return;
    }

    panel.setMutationLoading(true);
    try {
      await request<boolean>(`/v1/plugins/${panel.uninstallTarget.pluginCode}/uninstall`, {
        method: 'POST',
        data: { removeData: panel.removePluginData },
        ...API_OPTS.NO_REDIRECT,
      });
      message.success(
        panel.removePluginData
          ? formatMessage({ id: 'page.plugins.success.uninstalledAndDeleted', defaultMessage: 'Plugin uninstalled and database data removed' })
          : formatMessage({ id: 'page.plugins.success.uninstalled', defaultMessage: 'Plugin uninstalled' }),
      );
      panel.setUninstallDialogOpen(false);
      panel.setUninstallTarget(null);
      await refreshAfterMutation();
    } catch (error) {
      handlePluginPageError(error, formatMessage({ id: 'page.plugins.error.uninstall', defaultMessage: 'Failed to uninstall plugin, please try again later' }));
    } finally {
      panel.setMutationLoading(false);
    }
  }, [handlePluginPageError, panel, refreshAfterMutation]);

  const confirmDisable = useCallback(async () => {
    if (!panel.disableTarget) {
      return;
    }

    panel.setMutationLoading(true);
    try {
      await request<boolean>(`/v1/plugins/${panel.disableTarget.pluginCode}/disable`, {
        method: 'POST',
        data: { purgeData: panel.purgePluginDataOnDisable },
        ...API_OPTS.NO_REDIRECT,
      });
      message.success(
        panel.purgePluginDataOnDisable
          ? formatMessage({ id: 'page.plugins.success.disabledAndPurged', defaultMessage: 'Plugin disabled and plugin tables removed' })
          : formatMessage({ id: 'page.plugins.success.disabled', defaultMessage: 'Plugin disabled' }),
      );
      panel.setDisableDialogOpen(false);
      panel.setDisableTarget(null);
      await refreshAfterMutation();
    } catch (error) {
      handlePluginPageError(error, formatMessage({ id: 'page.plugins.error.disable', defaultMessage: 'Failed to disable plugin, please try again later' }));
    } finally {
      panel.setMutationLoading(false);
    }
  }, [handlePluginPageError, panel, refreshAfterMutation]);

  const handleUpload = useCallback(async () => {
    if (!panel.uploadFile) {
      message.warning(formatMessage({ id: 'page.plugins.error.selectPackage', defaultMessage: 'Please choose a plugin package first' }));
      return;
    }
    if (!panel.uploadFile.name.toLowerCase().endsWith('.zip')) {
      message.warning(formatMessage({ id: 'page.plugins.error.zipOnly', defaultMessage: 'Only zip plugin packages are supported' }));
      return;
    }
    if (panel.uploadFile.size > 50 * 1024 * 1024) {
      message.warning(formatMessage({ id: 'page.plugins.error.max50mb', defaultMessage: 'The plugin package cannot exceed 50MB' }));
      return;
    }
    panel.setMutationLoading(true);
    try {
      const formData = new FormData();
      formData.append('file', panel.uploadFile);
      await request('/v1/plugins/upload', {
        method: 'POST',
        headers: {},
        data: formData,
        ...API_OPTS.NO_REDIRECT,
      });
      panel.setUploadVisible(false);
      panel.setUploadFile(null);
      message.success(formatMessage({ id: 'page.plugins.success.uploaded', defaultMessage: 'Plugin uploaded and validated' }));
      await loadOverview();
    } catch (error) {
      handlePluginPageError(error, formatMessage({ id: 'page.plugins.error.upload', defaultMessage: 'Failed to upload plugin, please try again later' }));
    } finally {
      panel.setMutationLoading(false);
    }
  }, [handlePluginPageError, loadOverview, panel]);

  const handleOpenVersions = useCallback((plugin: PluginDefinition) => {
    panel.setSelectedPlugin(plugin);
    panel.setVersionDrawerOpen(true);
  }, [panel]);

  const handleOpenDetails = useCallback((plugin: PluginDefinition) => {
    panel.setSelectedPlugin(plugin);
    panel.setDetailDrawerOpen(true);
  }, [panel]);

  const handleOpenLogs = useCallback(
    async (plugin: PluginDefinition) => {
      panel.setSelectedPlugin(plugin);
      panel.setLogDrawerOpen(true);
      panel.setLogsLoading(true);
      try {
        panel.setRuntimeLogs(await runtimeLogs(plugin.pluginCode, API_OPTS.NO_REDIRECT));
      } catch (error) {
        handlePluginPageError(error, formatMessage({ id: 'page.plugins.error.logs', defaultMessage: 'Failed to load plugin logs, please try again later' }));
      } finally {
        panel.setLogsLoading(false);
      }
    },
    [handlePluginPageError, panel],
  );

  return {
    handleInstall,
    handleActivate,
    handleEnable,
    handleDisable,
    handleRollback,
    handleUninstall,
    confirmUninstall,
    confirmDisable,
    handleUpload,
    handleOpenVersions,
    handleOpenDetails,
    handleOpenLogs,
  };
};

type UsePluginManagementActionsParams = {
  loading: boolean;
  definitions: PluginDefinition[];
  availablePlugins: TenantPlugin[];
  versionMap: Record<string, PluginVersion[]>;
  searchKeyword: string;
  loadOverview: () => Promise<void>;
};

const usePluginManagementActions = ({
  loading,
  definitions,
  availablePlugins,
  versionMap,
  searchKeyword,
  loadOverview,
}: UsePluginManagementActionsParams) => {
  const [selectedPlugin, setSelectedPlugin] = useState<PluginDefinition | null>(null);
  const [versionDrawerOpen, setVersionDrawerOpen] = useState(false);
  const [logDrawerOpen, setLogDrawerOpen] = useState(false);
  const [detailDrawerOpen, setDetailDrawerOpen] = useState(false);
  const [uploadVisible, setUploadVisible] = useState(false);
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [runtimeLogs, setRuntimeLogs] = useState<PluginRuntimeLog[]>([]);
  const [logsLoading, setLogsLoading] = useState(false);
  const [mutationLoading, setMutationLoading] = useState(false);
  const [uninstallDialogOpen, setUninstallDialogOpen] = useState(false);
  const [uninstallTarget, setUninstallTarget] = useState<PluginDefinition | null>(null);
  const [removePluginData, setRemovePluginData] = useState(false);
  const [disableDialogOpen, setDisableDialogOpen] = useState(false);
  const [disableTarget, setDisableTarget] = useState<PluginDefinition | null>(null);
  const [purgePluginDataOnDisable, setPurgePluginDataOnDisable] = useState(false);
  const panel: PluginPanelState = {
    selectedPlugin,
    setSelectedPlugin,
    versionDrawerOpen,
    setVersionDrawerOpen,
    logDrawerOpen,
    setLogDrawerOpen,
    detailDrawerOpen,
    setDetailDrawerOpen,
    uploadVisible,
    setUploadVisible,
    uploadFile,
    setUploadFile,
    runtimeLogs,
    setRuntimeLogs,
    logsLoading,
    setLogsLoading,
    mutationLoading,
    setMutationLoading,
    uninstallDialogOpen,
    setUninstallDialogOpen,
    uninstallTarget,
    setUninstallTarget,
    removePluginData,
    setRemovePluginData,
    disableDialogOpen,
    setDisableDialogOpen,
    disableTarget,
    setDisableTarget,
    purgePluginDataOnDisable,
    setPurgePluginDataOnDisable,
  };
  const { token } = theme.useToken();
  const { actionPermission, responsive } = usePagePermissionActions();
  const detailDescriptionsProps = useDetailDescriptionsProps({ column: responsive.isMobile ? 1 : 2 });
  useEffect(() => {
    if (!selectedPlugin && definitions.length) {
      setSelectedPlugin(definitions[0] || null);
    }
  }, [definitions, selectedPlugin]);
  const currentAvailableMap = useMemo(() => buildAvailablePluginMap(availablePlugins), [availablePlugins]);
  const filteredDefinitions = useMemo(() => filterPluginDefinitions(definitions, searchKeyword), [definitions, searchKeyword]);
  const selectedPluginVersions = selectedPlugin ? versionMap[selectedPlugin.pluginCode] || [] : [];
  const selectedTenantPlugin = selectedPlugin ? currentAvailableMap.get(selectedPlugin.pluginCode) : undefined;
  const selectedActiveVersion = selectedPluginVersions.find((item) => item.isActive === 1) || selectedPluginVersions[0];
  const getPreferredEnableVersionForPlugin = useCallback((pluginCode: string) => getPreferredEnableVersion(pluginCode, versionMap), [versionMap]);
  const canUploadPlugin = actionPermission.can('plugin:management:upload');
  const canInstallPlugin = actionPermission.can('plugin:management:install');
  const canUpgradePlugin = actionPermission.can('plugin:management:upgrade');
  const canRollbackPlugin = actionPermission.can('plugin:management:rollback');
  const canEnablePlugin = actionPermission.can('plugin:management:enable');
  const canDisablePlugin = actionPermission.can('plugin:management:disable');
  const canViewPluginLogs = actionPermission.can('plugin:management:logs');
  const {
    handleInstall,
    handleActivate,
    handleEnable,
    handleDisable,
    handleRollback,
    handleUninstall,
    confirmUninstall,
    confirmDisable,
    handleUpload,
    handleOpenVersions,
    handleOpenDetails,
    handleOpenLogs,
  } = usePluginMutationActions({
    definitions,
    versionMap,
    loadOverview,
    panel,
  });
  const versionColumns = useMemo(
    () =>
      buildVersionColumns({
        isDesktop: responsive.isDesktop,
        isMobile: responsive.isMobile,
        canInstall: canInstallPlugin,
        canUpgrade: canUpgradePlugin,
        canRollback: canRollbackPlugin,
        canDisable: canDisablePlugin,
        onInstall: (pluginCode, version) => void handleInstall(pluginCode, version),
        onActivate: (pluginCode, version) => void handleActivate(pluginCode, version),
        onDisable: (pluginCode) => void handleDisable(pluginCode),
        onRollback: (pluginCode, version) => void handleRollback(pluginCode, version),
      }),
    [canDisablePlugin, canInstallPlugin, canRollbackPlugin, canUpgradePlugin, handleActivate, handleDisable, handleInstall, handleRollback, responsive.isDesktop, responsive.isMobile],
  );

  return {
    token,
    responsive,
    detailDescriptionsProps,
    loading,
    currentAvailableMap,
    filteredDefinitions,
    selectedPluginVersions,
    selectedTenantPlugin,
    selectedActiveVersion,
    canUploadPlugin,
    canEnablePlugin,
    canDisablePlugin,
    canViewPluginLogs,
    getPreferredEnableVersionForPlugin,
    versionColumns,
    ...panel,
    handleUpload,
    handleOpenVersions,
    handleOpenDetails,
    handleOpenLogs,
    handleUninstall,
    handleEnable,
    handleDisable,
    confirmUninstall,
    confirmDisable,
  };
};

const PluginVersionDrawer = ({
  responsive,
  selectedPlugin,
  selectedPluginVersions,
  selectedTenantPlugin,
  selectedActiveVersion,
  mutationLoading,
  versionDrawerOpen,
  versionColumns,
  setVersionDrawerOpen,
}: {
  responsive: { isMobile: boolean };
  selectedPlugin: PluginDefinition | null;
  selectedPluginVersions: PluginVersion[];
  selectedTenantPlugin?: TenantPlugin;
  selectedActiveVersion?: PluginVersion;
  mutationLoading: boolean;
  versionDrawerOpen: boolean;
  versionColumns: ProColumns<PluginVersion>[];
  setVersionDrawerOpen: (open: boolean) => void;
}) => (
  <ManagementDrawer
    title={
      selectedPlugin
        ? `${selectedPlugin.pluginName} · ${formatMessage({ id: 'page.plugins.versionManagement', defaultMessage: 'Version management' })}`
        : formatMessage({ id: 'page.plugins.versionManagement', defaultMessage: 'Version management' })
    }
    open={versionDrawerOpen}
    onClose={() => setVersionDrawerOpen(false)}
  >
    <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile)} style={{ width: '100%' }}>
      <Descriptions bordered column={responsive.isMobile ? 1 : 2} size="small">
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.pluginCode', defaultMessage: 'Plugin code' })}>{selectedPlugin?.pluginCode || '-'}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.currentEnabledVersion', defaultMessage: 'Current enabled version' })}>{selectedTenantPlugin?.version || selectedActiveVersion?.version || '-'}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.versionCount', defaultMessage: 'Version count' })}>{selectedPluginVersions.length}</Descriptions.Item>
      </Descriptions>
      <ManagementTable
        rowKey={(record) => `${record.pluginCode}-${record.version}`}
        loading={mutationLoading}
        dataSource={selectedPluginVersions}
        pagination={false}
        columns={versionColumns}
        isMobile={responsive.isMobile}
        search={false}
        toolBarRender={false}
      />
    </Space>
  </ManagementDrawer>
);

const PluginDetailDrawer = ({
  detailDescriptionsProps,
  selectedPlugin,
  selectedTenantPlugin,
  selectedActiveVersion,
  detailDrawerOpen,
  setDetailDrawerOpen,
}: {
  detailDescriptionsProps: DescriptionsProps;
  selectedPlugin: PluginDefinition | null;
  selectedTenantPlugin?: TenantPlugin;
  selectedActiveVersion?: PluginVersion;
  detailDrawerOpen: boolean;
  setDetailDrawerOpen: (open: boolean) => void;
}) => (
  <ManagementDrawer
    title={selectedPlugin ? `${selectedPlugin.pluginName} · ${formatMessage({ id: 'page.plugins.detail', defaultMessage: 'Details' })}` : formatMessage({ id: 'page.plugins.detail', defaultMessage: 'Plugin details' })}
    open={detailDrawerOpen}
    onClose={() => setDetailDrawerOpen(false)}
  >
    {selectedPlugin ? (
      <Descriptions {...detailDescriptionsProps}>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.pluginCode', defaultMessage: 'Plugin code' })}>{selectedPlugin.pluginCode}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.name', defaultMessage: 'Plugin name' })}>{selectedPlugin.pluginName}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.description', defaultMessage: 'Description' })}>{selectedPlugin.description || '-'}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.author', defaultMessage: 'Author' })}>{selectedPlugin.author || '-'}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.apiVersion', defaultMessage: 'API version' })}>{selectedPlugin.pluginApiVersion}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.status', defaultMessage: 'Status' })}>{selectedPlugin.status}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.currentVersion', defaultMessage: 'Current version' })}>{selectedTenantPlugin?.version || selectedActiveVersion?.version || '-'}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.enabled', defaultMessage: 'Enabled' })}>{selectedTenantPlugin ? formatMessage({ id: 'page.plugins.enabled.true', defaultMessage: 'Enabled' }) : formatMessage({ id: 'page.plugins.enabled.false', defaultMessage: 'Disabled' })}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.schemaMode', defaultMessage: 'Schema mode' })}>{selectedPlugin.schemaMode || '-'}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.lifecycleStatus', defaultMessage: 'Lifecycle status' })}>{selectedTenantPlugin?.lifecycleStatus || selectedActiveVersion?.lifecycleStatus || '-'}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.schemaStatus', defaultMessage: 'Schema status' })}>{selectedTenantPlugin?.schemaStatus || selectedActiveVersion?.schemaStatus || '-'}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.hotDisable', defaultMessage: 'Hot disable' })}>{selectedPlugin.supportsHotDisable ? t('支持', 'Supported') : t('不支持', 'Not supported')}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.dataPurge', defaultMessage: 'Data purge' })}>{selectedPlugin.supportsDataPurge ? t('支持', 'Supported') : t('不支持', 'Not supported')}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.menuCount', defaultMessage: 'Menu count' })}>{selectedTenantPlugin?.menus?.length || 0}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.routeCount', defaultMessage: 'Route count' })}>{selectedTenantPlugin?.routes?.length || 0}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.runtimeContributions', defaultMessage: 'Runtime contributions' })}>
          {(selectedTenantPlugin?.runtimeContributions || selectedPlugin.runtimeContributions || []).join(', ') || '-'}
        </Descriptions.Item>
      </Descriptions>
    ) : null}
  </ManagementDrawer>
);

const PluginLogDrawer = ({
  responsive,
  selectedPlugin,
  runtimeLogs,
  logsLoading,
  logDrawerOpen,
  setLogDrawerOpen,
}: {
  responsive: { isMobile: boolean };
  selectedPlugin: PluginDefinition | null;
  runtimeLogs: PluginRuntimeLog[];
  logsLoading: boolean;
  logDrawerOpen: boolean;
  setLogDrawerOpen: (open: boolean) => void;
}) => {
  const logColumns: ProColumns<PluginRuntimeLog>[] = [
    { title: t('时间', 'Time'), dataIndex: 'createdAt', width: 'var(--saas-spacing-180)' },
    { title: t('操作类型', 'Operation type'), dataIndex: 'operationType', width: 'var(--saas-spacing-120)' },
    { title: t('生命周期', 'Lifecycle'), dataIndex: 'lifecycleStatus', width: 'var(--saas-spacing-120)' },
    { title: t('结果', 'Result'), dataIndex: 'resultStatus', width: 'var(--saas-spacing-120)' },
    {
      title: t('详情', 'Details'),
      dataIndex: 'detailMessage',
      responsive: ['lg', 'xl', 'xxl'],
      ellipsis: true,
      render: (_, record) =>
        record.detailMessage ? (
          <Typography.Text copyable={{ text: record.detailMessage }} ellipsis={{ tooltip: record.detailMessage }}>
            {record.detailMessage}
          </Typography.Text>
        ) : (
          '-'
        ),
    },
  ];

  return (
    <ManagementDrawer
      title={selectedPlugin ? `${selectedPlugin.pluginName} · ${formatMessage({ id: 'page.plugins.log', defaultMessage: 'Logs' })}` : formatMessage({ id: 'page.plugins.log', defaultMessage: 'Plugin logs' })}
      open={logDrawerOpen}
      onClose={() => setLogDrawerOpen(false)}
    >
      <ManagementTable
        rowKey="id"
        loading={logsLoading}
        dataSource={runtimeLogs}
        pagination={false}
        columns={logColumns}
        isMobile={responsive.isMobile}
        search={false}
        toolBarRender={false}
      />
    </ManagementDrawer>
  );
};

const PluginUninstallModal = ({
  token,
  isMobile,
  uninstallDialogOpen,
  uninstallTarget,
  removePluginData,
  mutationLoading,
  setUninstallDialogOpen,
  setUninstallTarget,
  setRemovePluginData,
  confirmUninstall,
}: {
  token: { colorBorderSecondary: string; colorPrimary: string; colorBgContainer: string; colorPrimaryBg: string; colorError: string; colorErrorBg: string };
  uninstallDialogOpen: boolean;
  uninstallTarget: PluginDefinition | null;
  removePluginData: boolean;
  mutationLoading: boolean;
  setUninstallDialogOpen: (open: boolean) => void;
  setUninstallTarget: (plugin: PluginDefinition | null) => void;
  setRemovePluginData: (remove: boolean) => void;
  confirmUninstall: () => Promise<void>;
  isMobile: boolean;
}) => {
  const rowGutterPanel = resolveResponsiveValue(APP_SPACING.rowGutterPanel, isMobile);
  const microOffset = resolveResponsiveValue(APP_SPACING.microOffset, isMobile);
  const rowGutterPadHorizontal = rowGutterPanel[1] + microOffset;

  return (
    <Modal
      title={
        uninstallTarget
          ? formatMessage({ id: 'page.plugins.uninstallWithName', defaultMessage: 'Uninstall {name}' }, { name: uninstallTarget.pluginName })
          : formatMessage({ id: 'page.plugins.uninstall', defaultMessage: 'Uninstall plugin' })
      }
      open={uninstallDialogOpen}
      onCancel={() => {
        if (mutationLoading) {
          return;
        }
        setUninstallDialogOpen(false);
        setUninstallTarget(null);
      }}
      okText={formatMessage({ id: 'page.plugins.confirm', defaultMessage: 'Confirm uninstall' })}
      cancelText={formatMessage({ id: 'page.plugins.cancel', defaultMessage: 'Cancel' })}
      confirmLoading={mutationLoading}
      onOk={() => void confirmUninstall()}
      destroyOnHidden
    >
      <Space
        direction="vertical"
        size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)}
        style={{ width: '100%' }}
      >
        <Typography.Paragraph style={{ marginBottom: 0 }}>
          {formatMessage({ id: 'page.plugins.confirmUninstall', defaultMessage: 'You are about to uninstall {name}.' }, { name: uninstallTarget?.pluginName || uninstallTarget?.pluginCode || '-' })}
        </Typography.Paragraph>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          {formatMessage({ id: 'page.plugins.uninstallDesc', defaultMessage: 'You can choose whether to delete the plugin-related database data as well. If selected, plugin runtime logs, platform bindings, version records, and plugin definitions will be removed.' })}
        </Typography.Paragraph>
        <Radio.Group value={removePluginData} onChange={(event) => setRemovePluginData(event.target.value)} style={{ width: '100%' }}>
          <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.modalFooterGap, isMobile)} style={{ width: '100%' }}>
            <Radio
              value={false}
              style={{
                display: 'flex',
                alignItems: 'center',
                width: '100%',
                marginInlineStart: 0,
                padding: `${rowGutterPanel[0]}px ${rowGutterPadHorizontal}px`,
                borderRadius: 'var(--saas-card-radius)',
                border: `1px solid ${removePluginData ? token.colorBorderSecondary : token.colorPrimary}`,
                background: removePluginData ? token.colorBgContainer : token.colorPrimaryBg,
              }}
            >
              {formatMessage({ id: 'page.plugins.onlyUninstall', defaultMessage: 'Only uninstall the plugin, do not delete database data' })}
            </Radio>
            <Radio
              value={true}
              style={{
                display: 'flex',
                alignItems: 'center',
                width: '100%',
                marginInlineStart: 0,
                padding: `${rowGutterPanel[0]}px ${rowGutterPadHorizontal}px`,
                borderRadius: 'var(--saas-card-radius)',
                border: `1px solid ${removePluginData ? token.colorError : token.colorBorderSecondary}`,
                background: removePluginData ? token.colorErrorBg : token.colorBgContainer,
              }}
            >
              {formatMessage({ id: 'page.plugins.uninstallAndDeleteData', defaultMessage: 'Uninstall and delete database data' })}
            </Radio>
          </Space>
        </Radio.Group>
      </Space>
    </Modal>
  );
};

const PluginDisableModal = ({
  token,
  isMobile,
  disableDialogOpen,
  disableTarget,
  purgePluginDataOnDisable,
  mutationLoading,
  setDisableDialogOpen,
  setDisableTarget,
  setPurgePluginDataOnDisable,
  confirmDisable,
}: {
  token: { colorBorderSecondary: string; colorPrimary: string; colorBgContainer: string; colorPrimaryBg: string; colorError: string; colorErrorBg: string };
  disableDialogOpen: boolean;
  disableTarget: PluginDefinition | null;
  purgePluginDataOnDisable: boolean;
  mutationLoading: boolean;
  setDisableDialogOpen: (open: boolean) => void;
  setDisableTarget: (plugin: PluginDefinition | null) => void;
  setPurgePluginDataOnDisable: (remove: boolean) => void;
  confirmDisable: () => Promise<void>;
  isMobile: boolean;
}) => {
  const rowGutterPanel = resolveResponsiveValue(APP_SPACING.rowGutterPanel, isMobile);
  const microOffset = resolveResponsiveValue(APP_SPACING.microOffset, isMobile);
  const rowGutterPadHorizontal = rowGutterPanel[1] + microOffset;

  return (
    <Modal
      title={
        disableTarget
          ? formatMessage({ id: 'page.plugins.disableWithName', defaultMessage: 'Disable {name}' }, { name: disableTarget.pluginName })
          : formatMessage({ id: 'page.plugins.disable', defaultMessage: 'Disable plugin' })
      }
      open={disableDialogOpen}
      onCancel={() => {
        if (mutationLoading) {
          return;
        }
        setDisableDialogOpen(false);
        setDisableTarget(null);
      }}
      okText={formatMessage({ id: 'page.plugins.confirm', defaultMessage: 'Confirm' })}
      cancelText={formatMessage({ id: 'page.plugins.cancel', defaultMessage: 'Cancel' })}
      confirmLoading={mutationLoading}
      onOk={() => void confirmDisable()}
      destroyOnHidden
    >
      <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.sectionGap, isMobile)} style={{ width: '100%' }}>
        <Typography.Paragraph style={{ marginBottom: 0 }}>
          {formatMessage({ id: 'page.plugins.confirmDisable', defaultMessage: 'You are about to disable {name}.' }, { name: disableTarget?.pluginName || disableTarget?.pluginCode || '-' })}
        </Typography.Paragraph>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          {formatMessage({ id: 'page.plugins.disableDesc', defaultMessage: 'Choose whether to keep plugin data for later re-enable, or remove plugin-owned tables and data now.' })}
        </Typography.Paragraph>
        <Radio.Group value={purgePluginDataOnDisable} onChange={(event) => setPurgePluginDataOnDisable(event.target.value)} style={{ width: '100%' }}>
          <Space direction="vertical" size={resolveResponsiveValue(APP_SPACING.modalFooterGap, isMobile)} style={{ width: '100%' }}>
            <Radio
              value={false}
              style={{
                display: 'flex',
                alignItems: 'center',
                width: '100%',
                marginInlineStart: 0,
                padding: `${rowGutterPanel[0]}px ${rowGutterPadHorizontal}px`,
                borderRadius: 'var(--saas-card-radius)',
                border: `1px solid ${purgePluginDataOnDisable ? token.colorBorderSecondary : token.colorPrimary}`,
                background: purgePluginDataOnDisable ? token.colorBgContainer : token.colorPrimaryBg,
              }}
            >
              {formatMessage({ id: 'page.plugins.disableKeepData', defaultMessage: 'Disable only and keep data for later recovery' })}
            </Radio>
            <Radio
              value={true}
              style={{
                display: 'flex',
                alignItems: 'center',
                width: '100%',
                marginInlineStart: 0,
                padding: `${rowGutterPanel[0]}px ${rowGutterPadHorizontal}px`,
                borderRadius: 'var(--saas-card-radius)',
                border: `1px solid ${purgePluginDataOnDisable ? token.colorError : token.colorBorderSecondary}`,
                background: purgePluginDataOnDisable ? token.colorErrorBg : token.colorBgContainer,
              }}
            >
              {formatMessage({ id: 'page.plugins.disableAndDeleteData', defaultMessage: 'Disable and remove plugin-owned tables/data' })}
            </Radio>
          </Space>
        </Radio.Group>
      </Space>
    </Modal>
  );
};

const PluginUploadModal = ({
  uploadVisible,
  canUploadPlugin,
  mutationLoading,
  setUploadVisible,
  setUploadFile,
  handleUpload,
}: {
  uploadVisible: boolean;
  canUploadPlugin: boolean;
  mutationLoading: boolean;
  setUploadVisible: (open: boolean) => void;
  setUploadFile: (file: File | null) => void;
  handleUpload: () => Promise<void>;
}) => (
  <Modal
    open={uploadVisible}
    title={formatMessage({ id: 'page.plugins.uploadPackage', defaultMessage: 'Upload plugin package' })}
    onCancel={() => setUploadVisible(false)}
    onOk={() => {
      if (canUploadPlugin) {
        void handleUpload();
      }
    }}
    confirmLoading={mutationLoading}
    okButtonProps={{ disabled: !canUploadPlugin }}
    okText={formatMessage({ id: 'page.plugins.uploadConfirm', defaultMessage: 'Upload' })}
    cancelText={formatMessage({ id: 'page.plugins.cancelUpload', defaultMessage: 'Cancel' })}
  >
    <Upload
      beforeUpload={(file) => {
        setUploadFile(file);
        return false;
      }}
      maxCount={1}
      accept=".zip"
      onRemove={() => setUploadFile(null)}
    >
      <Button icon={<CloudUploadOutlined />}>{formatMessage({ id: 'page.plugins.chooseZip', defaultMessage: 'Choose zip plugin package' })}</Button>
    </Upload>
  </Modal>
);

const PluginsPage = () => {
  const { token } = theme.useToken();
  const { pluginPageDataPack } = usePluginsPageData();
  const pluginActions = usePluginManagementActions({
    loading: pluginPageDataPack.loading,
    definitions: pluginPageDataPack.definitions,
    availablePlugins: pluginPageDataPack.availablePlugins,
    versionMap: pluginPageDataPack.versionMap,
    searchKeyword: pluginPageDataPack.searchKeyword,
    loadOverview: pluginPageDataPack.loadOverview,
  });

  const {
    responsive,
    detailDescriptionsProps,
    loading,
    filteredDefinitions,
    currentAvailableMap,
    selectedPlugin,
    selectedPluginVersions,
    selectedTenantPlugin,
    selectedActiveVersion,
    runtimeLogs,
    logsLoading,
    mutationLoading,
    uninstallDialogOpen,
    uninstallTarget,
    removePluginData,
    disableDialogOpen,
    disableTarget,
    purgePluginDataOnDisable,
    versionDrawerOpen,
    logDrawerOpen,
    detailDrawerOpen,
    uploadVisible,
    canUploadPlugin,
    canEnablePlugin,
    canDisablePlugin,
    canViewPluginLogs,
    getPreferredEnableVersionForPlugin,
    versionColumns,
    setSearchKeyword,
    setVersionDrawerOpen,
    setLogDrawerOpen,
    setDetailDrawerOpen,
    setUploadVisible,
    setUploadFile,
    setUninstallDialogOpen,
    setUninstallTarget,
    setRemovePluginData,
    setDisableDialogOpen,
    setDisableTarget,
    setPurgePluginDataOnDisable,
    handleUpload,
    handleOpenVersions,
    handleOpenDetails,
    handleOpenLogs,
    handleUninstall,
    handleEnable,
    handleDisable,
    confirmUninstall,
    confirmDisable,
  } = { ...pluginPageDataPack, ...pluginActions };

  return (
    <ManagementPage
      content={null}
      ghost
      title={formatMessage({ id: 'page.plugins.title', defaultMessage: 'Plugin management' })}
    >
      <ManagementPageBody>
        <Card
          bodyStyle={{
            display: 'flex',
            flexDirection: 'column',
            gap: resolveResponsiveValue(APP_SPACING.sectionGap, responsive.isMobile),
          }}
        >
          <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
            <Input.Search
              allowClear
              placeholder={formatMessage({ id: 'page.plugins.searchPlaceholder', defaultMessage: 'Enter plugin code or name' })}
              onChange={(event) => setSearchKeyword(event.target.value)}
              style={{ width: 'var(--saas-spacing-320)', maxWidth: '100%', flex: '0 1 var(--saas-spacing-320)' }}
            />
            <Space wrap>
              <Button icon={<SyncOutlined />} onClick={() => void pluginPageDataPack.loadOverview()} loading={loading || mutationLoading}>
                {formatMessage({ id: 'page.plugins.refresh', defaultMessage: 'Refresh' })}
              </Button>
              <Button icon={<CloudUploadOutlined />} type="primary" disabled={!canUploadPlugin} onClick={() => setUploadVisible(true)}>
                {formatMessage({ id: 'page.plugins.upload', defaultMessage: 'Upload plugin' })}
              </Button>
            </Space>
          </Space>
          <PluginCardsGrid
            loading={loading}
            definitions={filteredDefinitions}
            currentAvailableMap={currentAvailableMap}
            getPreferredEnableVersion={getPreferredEnableVersionForPlugin}
            mutationLoading={mutationLoading}
            canEnable={canEnablePlugin}
            canDisable={canDisablePlugin}
            canViewLogs={canViewPluginLogs}
            isMobile={responsive.isMobile}
            onToggleEnable={(pluginCode, enabled, versionLabel) =>
              void (enabled ? handleEnable(pluginCode, versionLabel) : handleDisable(pluginCode))
            }
            onOpenDetails={handleOpenDetails}
            onOpenVersions={handleOpenVersions}
            onOpenLogs={(plugin) => void handleOpenLogs(plugin)}
            onUninstall={handleUninstall}
          />
        </Card>
      </ManagementPageBody>
      <PluginVersionDrawer
        responsive={responsive}
        selectedPlugin={selectedPlugin}
        selectedPluginVersions={selectedPluginVersions}
        selectedTenantPlugin={selectedTenantPlugin}
        selectedActiveVersion={selectedActiveVersion}
        mutationLoading={mutationLoading}
        versionDrawerOpen={versionDrawerOpen}
        versionColumns={versionColumns}
        setVersionDrawerOpen={setVersionDrawerOpen}
      />
      <PluginDetailDrawer
        detailDescriptionsProps={detailDescriptionsProps}
        selectedPlugin={selectedPlugin}
        selectedTenantPlugin={selectedTenantPlugin}
        selectedActiveVersion={selectedActiveVersion}
        detailDrawerOpen={detailDrawerOpen}
        setDetailDrawerOpen={setDetailDrawerOpen}
      />
      <PluginLogDrawer
        responsive={responsive}
        selectedPlugin={selectedPlugin}
        runtimeLogs={runtimeLogs}
        logsLoading={logsLoading}
        logDrawerOpen={logDrawerOpen}
        setLogDrawerOpen={setLogDrawerOpen}
      />
      <PluginUninstallModal
        token={token}
        uninstallDialogOpen={uninstallDialogOpen}
        uninstallTarget={uninstallTarget}
        removePluginData={removePluginData}
        mutationLoading={mutationLoading}
        setUninstallDialogOpen={setUninstallDialogOpen}
        setUninstallTarget={setUninstallTarget}
        setRemovePluginData={setRemovePluginData}
        confirmUninstall={confirmUninstall}
        isMobile={responsive.isMobile}
      />
      <PluginDisableModal
        token={token}
        disableDialogOpen={disableDialogOpen}
        disableTarget={disableTarget}
        purgePluginDataOnDisable={purgePluginDataOnDisable}
        mutationLoading={mutationLoading}
        setDisableDialogOpen={setDisableDialogOpen}
        setDisableTarget={setDisableTarget}
        setPurgePluginDataOnDisable={setPurgePluginDataOnDisable}
        confirmDisable={confirmDisable}
        isMobile={responsive.isMobile}
      />
      <PluginUploadModal
        uploadVisible={uploadVisible}
        canUploadPlugin={canUploadPlugin}
        mutationLoading={mutationLoading}
        setUploadVisible={setUploadVisible}
        setUploadFile={setUploadFile}
        handleUpload={handleUpload}
      />
    </ManagementPage>
  );
};

export default PluginsPage;
