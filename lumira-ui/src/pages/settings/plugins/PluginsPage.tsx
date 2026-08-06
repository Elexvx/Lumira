import { formatMessage } from '@/i18n/formatMessage';
import { CloudUploadOutlined, SyncOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Descriptions, Input, Modal, Radio, Space, Tag, Typography, Upload, theme } from 'antd';
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
import { request } from '@/services/common/request';
import type { MenuNode, PluginDefinition, PluginVersion, PluginAvailability } from '@/types/api';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';
import { confirmAction } from '@/utils/confirm';
import { APP_SPACING, resolveResponsiveValue } from '@/theme/spacing';
import { localizeBuiltinPluginDefinition, localizePluginValue } from './pluginPresentation';
import { refreshPluginMutationSession } from './pluginMutationSession';
import { PluginCardsGrid } from './PluginCardsGrid';

const pluginMessage = (id: string, defaultMessage: string) => formatMessage({ id, defaultMessage });
const pluginValue = (value?: string | null) => localizePluginValue(value, pluginMessage);

const usePluginsPageData = () => {
  const { setInitialState } = useInitialStateModel();
  const [definitions, setDefinitions] = useState<PluginDefinition[]>([]);
  const [availablePlugins, setAvailablePlugins] = useState<PluginAvailability[]>([]);
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
      const [definitionList, fetchedAvailablePlugins, versionResult] = await Promise.all([
        request<PluginDefinition[]>('/v1/plugins/definitions', {
          method: 'GET',
          ...API_OPTS.NO_REDIRECT,
        }),
        request<PluginAvailability[]>('/v1/plugins/current/available', {
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
      setAvailablePlugins(fetchedAvailablePlugins);
      setVersionMap(nextVersionMap);

      setInitialState((prev) =>
        prev
          ? {
              ...prev,
              availablePlugins: fetchedAvailablePlugins,
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

const buildAvailablePluginMap = (availablePlugins: PluginAvailability[]) =>
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
  { title: pluginMessage('page.plugins.version', 'Version'), dataIndex: 'version' },
  { title: pluginMessage('page.plugins.installStatus', 'Install status'), dataIndex: 'installStatus', render: (_, record) => pluginValue(record.installStatus) },
  { title: pluginMessage('page.plugins.loadStatus', 'Load status'), dataIndex: 'loadStatus', render: (_, record) => pluginValue(record.loadStatus) },
  { title: pluginMessage('page.plugins.healthStatus', 'Health status'), dataIndex: 'healthStatus', render: (_, record) => pluginValue(record.healthStatus) },
  {
    title: pluginMessage('page.plugins.active', 'Active'),
    dataIndex: 'isActive',
    render: (_, record) => (
      <Tag color={record.isActive === 1 ? 'green' : 'default'}>
        {record.isActive === 1
          ? pluginMessage('page.plugins.yes', 'Yes')
          : pluginMessage('page.plugins.no', 'No')}
      </Tag>
    ),
  },
  {
    title: pluginMessage('page.plugins.actions', 'Actions'),
    fixed: isDesktop ? 'right' : undefined,
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={[
          { key: 'install', label: pluginMessage('page.plugins.installAction', 'Install'), disabled: !canInstall, onClick: () => onInstall(record.pluginCode, record.version) },
          { key: 'activate', label: pluginMessage('page.plugins.activateAction', 'Activate'), disabled: !canUpgrade, onClick: () => onActivate(record.pluginCode, record.version) },
          { key: 'disable', label: pluginMessage('page.plugins.disableAction', 'Disable'), disabled: !canDisable, onClick: () => onDisable(record.pluginCode), danger: true },
          { key: 'rollback', label: pluginMessage('page.plugins.rollbackAction', 'Rollback'), disabled: !canRollback, onClick: () => onRollback(record.pluginCode, record.version) },
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
  detailDrawerOpen: boolean;
  setDetailDrawerOpen: (value: boolean | ((current: boolean) => boolean)) => void;
  uploadVisible: boolean;
  setUploadVisible: (value: boolean | ((current: boolean) => boolean)) => void;
  uploadFile: File | null;
  setUploadFile: (value: File | null | ((current: File | null) => File | null)) => void;
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
      request<PluginAvailability[]>('/v1/plugins/current/available', {
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
    const sessionState = await refreshPluginMutationSession();
    if (sessionState !== 'ready') {
      if (sessionState === 'temporarily_unavailable') {
        message.warning(
          formatMessage({
            id: 'page.plugins.error.sessionRefresh',
            defaultMessage: '插件已更新，但账号权限暂时无法刷新，请稍后手动刷新页面。',
          }),
        );
      }
      return;
    }

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
      const purgeData = Boolean(panel.disableTarget.supportsDataPurge && panel.purgePluginDataOnDisable);
      await request<boolean>(`/v1/plugins/${panel.disableTarget.pluginCode}/disable`, {
        method: 'POST',
        data: { purgeData },
        ...API_OPTS.NO_REDIRECT,
      });
      message.success(
        purgeData
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

  const handleOpenDetails = useCallback((plugin: PluginDefinition) => {
    panel.setSelectedPlugin(plugin);
    panel.setDetailDrawerOpen(true);
  }, [panel]);

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
    handleOpenDetails,
  };
};

type UsePluginManagementActionsParams = {
  loading: boolean;
  definitions: PluginDefinition[];
  availablePlugins: PluginAvailability[];
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
  const [detailDrawerOpen, setDetailDrawerOpen] = useState(false);
  const [uploadVisible, setUploadVisible] = useState(false);
  const [uploadFile, setUploadFile] = useState<File | null>(null);
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
    detailDrawerOpen,
    setDetailDrawerOpen,
    uploadVisible,
    setUploadVisible,
    uploadFile,
    setUploadFile,
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
  const detailDescriptionsProps = useDetailDescriptionsProps({ column: 1 });
  useEffect(() => {
    if (!selectedPlugin && definitions.length) {
      setSelectedPlugin(definitions[0] || null);
    }
  }, [definitions, selectedPlugin]);
  const currentAvailableMap = useMemo(() => buildAvailablePluginMap(availablePlugins), [availablePlugins]);
  const filteredDefinitions = useMemo(() => filterPluginDefinitions(definitions, searchKeyword), [definitions, searchKeyword]);
  const selectedPluginVersions = selectedPlugin ? versionMap[selectedPlugin.pluginCode] || [] : [];
  const selectedPluginAvailability = selectedPlugin ? currentAvailableMap.get(selectedPlugin.pluginCode) : undefined;
  const selectedActiveVersion = selectedPluginVersions.find((item) => item.isActive === 1) || selectedPluginVersions[0];
  const getPreferredEnableVersionForPlugin = useCallback((pluginCode: string) => getPreferredEnableVersion(pluginCode, versionMap), [versionMap]);
  const canUploadPlugin = actionPermission.can('plugin:management:upload');
  const canInstallPlugin = actionPermission.can('plugin:management:install');
  const canUpgradePlugin = actionPermission.can('plugin:management:upgrade');
  const canRollbackPlugin = actionPermission.can('plugin:management:rollback');
  const canEnablePlugin = actionPermission.can('plugin:management:enable');
  const canDisablePlugin = actionPermission.can('plugin:management:disable');
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
    handleOpenDetails,
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
    selectedPluginAvailability,
    selectedActiveVersion,
    canUploadPlugin,
    canEnablePlugin,
    canDisablePlugin,
    getPreferredEnableVersionForPlugin,
    versionColumns,
    ...panel,
    handleUpload,
    handleOpenDetails,
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
  selectedPluginAvailability,
  selectedActiveVersion,
  mutationLoading,
  versionDrawerOpen,
  versionColumns,
  setVersionDrawerOpen,
}: {
  responsive: { isMobile: boolean };
  selectedPlugin: PluginDefinition | null;
  selectedPluginVersions: PluginVersion[];
  selectedPluginAvailability?: PluginAvailability;
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
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.currentEnabledVersion', defaultMessage: 'Current enabled version' })}>{selectedPluginAvailability?.version || selectedActiveVersion?.version || '-'}</Descriptions.Item>
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
  selectedPluginAvailability,
  selectedActiveVersion,
  detailDrawerOpen,
  setDetailDrawerOpen,
}: {
  detailDescriptionsProps: DescriptionsProps;
  selectedPlugin: PluginDefinition | null;
  selectedPluginAvailability?: PluginAvailability;
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
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.status', defaultMessage: 'Status' })}>{pluginValue(selectedPlugin.status)}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.currentVersion', defaultMessage: 'Current version' })}>{selectedPluginAvailability?.version || selectedActiveVersion?.version || '-'}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.enabled', defaultMessage: 'Enabled' })}>{selectedPluginAvailability ? formatMessage({ id: 'page.plugins.enabled.true', defaultMessage: 'Enabled' }) : formatMessage({ id: 'page.plugins.enabled.false', defaultMessage: 'Disabled' })}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.schemaMode', defaultMessage: 'Schema mode' })}>{pluginValue(selectedPlugin.schemaMode)}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.lifecycleStatus', defaultMessage: 'Lifecycle status' })}>{pluginValue(selectedPluginAvailability?.lifecycleStatus || selectedActiveVersion?.lifecycleStatus)}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.schemaStatus', defaultMessage: 'Schema status' })}>{pluginValue(selectedPluginAvailability?.schemaStatus || selectedActiveVersion?.schemaStatus)}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.hotDisable', defaultMessage: 'Hot disable' })}>{selectedPlugin.supportsHotDisable ? pluginMessage('page.plugins.supported', 'Supported') : pluginMessage('page.plugins.notSupported', 'Not supported')}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.dataPurge', defaultMessage: 'Data purge' })}>{selectedPlugin.supportsDataPurge ? pluginMessage('page.plugins.supported', 'Supported') : pluginMessage('page.plugins.notSupported', 'Not supported')}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.menuCount', defaultMessage: 'Menu count' })}>{selectedPluginAvailability?.menus?.length || 0}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.routeCount', defaultMessage: 'Route count' })}>{selectedPluginAvailability?.routes?.length || 0}</Descriptions.Item>
        <Descriptions.Item label={formatMessage({ id: 'page.plugins.runtimeContributions', defaultMessage: 'Runtime contributions' })}>
          {(selectedPluginAvailability?.runtimeContributions || selectedPlugin.runtimeContributions || []).join(', ') || '-'}
        </Descriptions.Item>
      </Descriptions>
    ) : null}
  </ManagementDrawer>
);

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
  supportsDataPurge,
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
  supportsDataPurge: boolean;
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
          {formatMessage({ id: 'page.plugins.confirmDisableMessage', defaultMessage: 'You are about to disable {name}.' }, { name: disableTarget?.pluginName || disableTarget?.pluginCode || '-' })}
        </Typography.Paragraph>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          {formatMessage({ id: 'page.plugins.disableDesc', defaultMessage: 'Choose whether to keep plugin data for later re-enable, or remove plugin-owned tables and data now.' })}
        </Typography.Paragraph>
        <Radio.Group
          value={supportsDataPurge ? purgePluginDataOnDisable : false}
          onChange={(event) => setPurgePluginDataOnDisable(supportsDataPurge ? event.target.value : false)}
          style={{ width: '100%' }}
        >
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
              disabled={!supportsDataPurge}
              style={{
                display: 'flex',
                alignItems: 'center',
                width: '100%',
                marginInlineStart: 0,
                padding: `${rowGutterPanel[0]}px ${rowGutterPadHorizontal}px`,
                borderRadius: 'var(--saas-card-radius)',
                border: `1px solid ${supportsDataPurge && purgePluginDataOnDisable ? token.colorError : token.colorBorderSecondary}`,
                background: supportsDataPurge && purgePluginDataOnDisable ? token.colorErrorBg : token.colorBgContainer,
              }}
            >
              {formatMessage({ id: 'page.plugins.disableAndDeleteData', defaultMessage: 'Disable and remove plugin-owned tables/data' })}
            </Radio>
          </Space>
        </Radio.Group>
        <Alert
          type={supportsDataPurge ? 'warning' : 'info'}
          showIcon
          message={supportsDataPurge
            ? formatMessage({
                id: 'page.plugins.disablePurgeWarning',
                defaultMessage: 'This permanently deletes plugin-owned data. Re-enabling recreates empty tables; deleted data cannot be recovered.',
              })
            : formatMessage({
                id: 'page.plugins.disablePurgeUnavailable',
                defaultMessage: 'Data removal is unavailable because this plugin has no verified down migration.',
              })}
        />
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
  const localizedDefinitions = pluginPageDataPack.definitions.map((plugin) =>
    localizeBuiltinPluginDefinition(
      plugin,
      (id, fallback) => formatMessage({ id, defaultMessage: fallback }),
    ));
  const pluginActions = usePluginManagementActions({
    loading: pluginPageDataPack.loading,
    definitions: localizedDefinitions,
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
    selectedPluginAvailability,
    selectedActiveVersion,
    mutationLoading,
    uninstallDialogOpen,
    uninstallTarget,
    removePluginData,
    disableDialogOpen,
    disableTarget,
    purgePluginDataOnDisable,
    versionDrawerOpen,
    detailDrawerOpen,
    uploadVisible,
    canUploadPlugin,
    canEnablePlugin,
    canDisablePlugin,
    getPreferredEnableVersionForPlugin,
    versionColumns,
    setSearchKeyword,
    setVersionDrawerOpen,
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
    handleOpenDetails,
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
            isMobile={responsive.isMobile}
            onToggleEnable={(pluginCode, enabled, versionLabel) =>
              void (enabled ? handleEnable(pluginCode, versionLabel) : handleDisable(pluginCode))
            }
            onOpenDetails={handleOpenDetails}
            onUninstall={handleUninstall}
          />
        </Card>
      </ManagementPageBody>
      <PluginVersionDrawer
        responsive={responsive}
        selectedPlugin={selectedPlugin}
        selectedPluginVersions={selectedPluginVersions}
        selectedPluginAvailability={selectedPluginAvailability}
        selectedActiveVersion={selectedActiveVersion}
        mutationLoading={mutationLoading}
        versionDrawerOpen={versionDrawerOpen}
        versionColumns={versionColumns}
        setVersionDrawerOpen={setVersionDrawerOpen}
      />
      <PluginDetailDrawer
        detailDescriptionsProps={detailDescriptionsProps}
        selectedPlugin={selectedPlugin}
        selectedPluginAvailability={selectedPluginAvailability}
        selectedActiveVersion={selectedActiveVersion}
        detailDrawerOpen={detailDrawerOpen}
        setDetailDrawerOpen={setDetailDrawerOpen}
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
        supportsDataPurge={Boolean(disableTarget?.supportsDataPurge)}
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
