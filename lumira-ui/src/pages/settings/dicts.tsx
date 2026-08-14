import { Form, Tag, Typography } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';
import { ManagementTable } from '@/features/management/ManagementTable';
import { useCrudDrawerState } from '@/features/crud/useCrudDrawerState';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { useStandardFormProps } from '@/features/form/config';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import type { PermissionAwareTableAction } from '@/features/permissions/useActionPermission';
import { TableActionBar, type TableActionItem } from '@/features/table/TableActionBar';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { request } from '@/services/common/request';
import type { DictItemMutationPayload, DictTypeMutationPayload } from '@/services/dict/types';
import { API_OPTS } from '@/utils/errorMessage';
import { DictManagementDrawers } from './dicts/components/DictManagementDrawers';
import type { ManagementTableProps } from '@/features/management/ManagementTable';
import type { DictItemRecord, DictTypeRecord } from '@/types/api';
import type { PagedResponse } from '@/features/table/proTableRequest';
import type { ProColumns } from '@ant-design/pro-components';
import { useCallback, useMemo, useState } from 'react';
import { confirmAction } from '@/utils/confirm';

import { databaseMessage } from '@/i18n/databaseMessage';

const t = databaseMessage;

const dictStatusLabelMap: Record<string, string> = {
  ENABLED: t('ui.settings.dicts.enabled'),
  DISABLED: t('ui.settings.dicts.disabled'),
};

const renderStatusLabel = (status?: string | null) => dictStatusLabelMap[status || ''] || status || '-';

const renderAdaptiveText = (value?: string | null) =>
  value ? (
    <Typography.Text
      ellipsis={{ tooltip: value }}
      style={{ display: 'block', width: '100%', minWidth: 0 }}
    >
      {value}
    </Typography.Text>
  ) : (
    '-'
  );

const renderCopyableRemark = (remark?: string | null) =>
  remark ? (
    <Typography.Text
      copyable={{ text: remark }}
      ellipsis={{ tooltip: remark }}
      style={{ display: 'inline-block', width: '100%', verticalAlign: 'middle' }}
    >
      {remark}
    </Typography.Text>
  ) : (
    '-'
  );

const buildDictItemColumns = ({
  isMobile,
  buildRowActions,
  onOpenEdit,
  onDelete,
}: {
  isMobile: boolean;
  buildRowActions: (items: PermissionAwareTableAction[]) => TableActionItem[];
  onOpenEdit: (record: DictItemRecord) => void;
  onDelete: (record: DictItemRecord) => void;
}): ProColumns<DictItemRecord>[] => [
  { title: t('ui.settings.dicts.label'), dataIndex: 'itemLabel' },
  { title: t('ui.settings.dicts.value'), dataIndex: 'itemValue' },
  { title: t('ui.settings.dicts.sortOrder'), dataIndex: 'sortNo', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
  {
    title: t('ui.settings.dicts.status'),
    dataIndex: 'status',
    search: false,
    render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{renderStatusLabel(record.status)}</Tag>,
  },
  {
    title: t('ui.settings.dicts.remark'),
    dataIndex: 'remark',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_, record) => renderCopyableRemark(record.remark),
  },
  {
    title: t('ui.settings.dicts.actions'),
    valueType: 'option',
    fixed: false,
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={buildRowActions([
          {
            key: 'edit',
            label: t('ui.settings.dicts.edit'),
            permission: 'system:dict:update',
            onClick: () => onOpenEdit(record),
          },
          {
            key: 'delete',
            label: t('ui.settings.dicts.delete'),
            permission: 'system:dict:delete',
            danger: true,
            onClick: () => onDelete(record),
          },
        ])}
      />
    ),
  },
];

const buildDictTypeColumns = ({
  isMobile,
  buildRowActions,
  onOpenDetail,
  onOpenEdit,
  onDelete,
}: {
  isMobile: boolean;
  buildRowActions: (items: PermissionAwareTableAction[]) => TableActionItem[];
  onOpenDetail: (record: DictTypeRecord) => void;
  onOpenEdit: (record: DictTypeRecord) => void;
  onDelete: (record: DictTypeRecord) => void;
}): ProColumns<DictTypeRecord>[] => [
  {
    title: t('ui.settings.dicts.dictionaryCode'),
    dataIndex: 'dictCode',
    search: true,
    ellipsis: true,
    render: (_, record) => renderAdaptiveText(record.dictCode),
  },
  {
    title: t('ui.settings.dicts.dictionaryName'),
    dataIndex: 'dictName',
    search: true,
    ellipsis: true,
    render: (_, record) => renderAdaptiveText(record.dictName),
  },
  {
    title: t('ui.settings.dicts.status'),
    dataIndex: 'status',
    valueEnum: {
      ENABLED: { text: t('ui.settings.dicts.enabled'), status: 'Success' },
      DISABLED: { text: t('ui.settings.dicts.disabled'), status: 'Default' },
    },
    render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{renderStatusLabel(record.status)}</Tag>,
  },
  {
    title: t('ui.settings.dicts.systemBuiltIn'),
    dataIndex: 'isSystem',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    render: (_, record) => <Tag color={Number(record.isSystem) !== 0 ? 'green' : 'default'}>{Number(record.isSystem) !== 0 ? t('ui.settings.dicts.yes') : t('ui.settings.dicts.no')}</Tag>,
  },
  {
    title: t('ui.settings.dicts.remark'),
    dataIndex: 'remark',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_, record) => renderCopyableRemark(record.remark),
  },
  {
    title: t('ui.settings.dicts.actions'),
    valueType: 'option',
    fixed: false,
    width: 'var(--saas-spacing-180)',
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={buildRowActions([
          {
            key: 'view',
            label: t('ui.settings.dicts.details'),
            permission: 'system:dict:view',
            onClick: () => onOpenDetail(record),
          },
          {
            key: 'edit',
            label: t('ui.settings.dicts.edit'),
            permission: 'system:dict:update',
            onClick: () => onOpenEdit(record),
          },
          {
            key: 'delete',
            label: t('ui.settings.dicts.delete'),
            permission: 'system:dict:delete',
            danger: true,
            disabled: Number(record.isSystem) !== 0,
            onClick: () => onDelete(record),
          },
        ])}
      />
    ),
  },
];

const useDictManagement = () => {
  const { actionPermission, responsive, searchConfig, buildToolbarButtons } = usePagePermissionActions();
  const typeCrud = useCrudPageState<DictTypeRecord>();
  const [typeForm] = Form.useForm<DictTypeMutationPayload>();
  const [typeDetail, setTypeDetail] = useState<DictTypeRecord | null>(null);
  const [typeItems, setTypeItems] = useState<DictItemRecord[]>([]);
  const [typeSaving, setTypeSaving] = useState(false);
  const canSaveType = actionPermission.can(typeCrud.drawer.editingId ? 'system:dict:update' : 'system:dict:create');
  const typeFormProps = useStandardFormProps({
    form: typeForm,
    initialValues: { status: 'ENABLED' },
  });
  const detailProps = useDetailProDescriptionsProps<DictTypeRecord>({
    column: responsive.isMobile ? 1 : 2,
    dataSource: typeDetail || undefined,
  });
  const itemDrawer = useCrudDrawerState<DictItemRecord>();
  const [itemForm] = Form.useForm<DictItemMutationPayload>();
  const [itemSaving, setItemSaving] = useState(false);
  const canSaveItem = actionPermission.can('system:dict:update');
  const itemFormProps = useStandardFormProps({
    form: itemForm,
    initialValues: { sortNo: 0, status: 'ENABLED' },
  });

  const refreshDictItems = useCallback(async (dictTypeId: number) => {
    const dictItems = await request<DictItemRecord[]>(`/v1/system/dict-types/${dictTypeId}/items`, {
      method: 'GET',
      ...API_OPTS.NO_REDIRECT,
    });
    setTypeItems(dictItems);
  }, [setTypeItems]);

  const openCreateItem = useCallback(() => {
    itemDrawer.openCreate();
    itemForm.resetFields();
    itemForm.setFieldsValue({ sortNo: 0, status: 'ENABLED' });
  }, [itemDrawer, itemForm]);

  const openEditItem = useCallback(
    (record: DictItemRecord) => {
      itemDrawer.openEdit(record, record.id);
      itemForm.setFieldsValue({
        itemLabel: record.itemLabel,
        itemValue: record.itemValue,
        sortNo: record.sortNo,
        status: record.status,
        remark: record.remark ?? undefined,
      });
    },
    [itemDrawer, itemForm],
  );

  const saveItem = useCallback(async () => {
    const dictTypeId = typeDetail?.id;
    if (!dictTypeId) {
      return;
    }
    setItemSaving(true);
    try {
      const values = await itemForm.validateFields();
      if (itemDrawer.editingId) {
        await request<DictItemRecord>(`/v1/system/dict-types/${dictTypeId}/items/${itemDrawer.editingId}`, {
          method: 'PUT',
          data: values,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('ui.settings.dicts.dictionaryItemUpdated'));
      } else {
        await request<DictItemRecord>(`/v1/system/dict-types/${dictTypeId}/items`, {
          method: 'POST',
          data: values,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('ui.settings.dicts.dictionaryItemCreated'));
      }
      itemDrawer.close();
      await refreshDictItems(dictTypeId);
      typeCrud.reloadTable();
    } finally {
      setItemSaving(false);
    }
  }, [itemDrawer, itemForm, refreshDictItems, setItemSaving, typeCrud, typeDetail?.id]);

  const deleteItem = useCallback(
    (record: DictItemRecord) => {
      const dictTypeId = typeDetail?.id;
      if (!dictTypeId) {
        return;
      }
      confirmAction({
        title: t('ui.settings.dicts.deleteDictionaryItem'),
        content: t('ui.settings.dicts.deleteDictionaryItem.6cb75e1a', { itemLabel: record.itemLabel }),
        okText: t('ui.settings.dicts.delete.0ad952f3'),
        okButtonProps: { danger: true },
        onOk: async () => {
          await request<boolean>(`/v1/system/dict-types/${dictTypeId}/items/${record.id}`, {
            method: 'DELETE',
            ...API_OPTS.NO_REDIRECT,
          });
          message.success('字典项已删除');
          await refreshDictItems(dictTypeId);
        },
      });
    },
    [refreshDictItems, typeDetail?.id],
  );

  const dictItemColumns = useMemo(
    () =>
      buildDictItemColumns({
        isMobile: responsive.isMobile,
        buildRowActions: actionPermission.buildTableActions,
        onOpenEdit: openEditItem,
        onDelete: deleteItem,
      }),
    [actionPermission.buildTableActions, deleteItem, openEditItem, responsive.isMobile],
  );
  const openDetail = useCallback(
    async (record: DictTypeRecord) => {
      typeCrud.detail.openDetail(record);
      typeCrud.detail.setLoading(true);
      try {
        const [detailResult, dictItems] = await Promise.all([
          request<DictTypeRecord>(`/v1/system/dict-types/${record.id}`, {
            method: 'GET',
            ...API_OPTS.NO_REDIRECT,
          }),
          request<DictItemRecord[]>(`/v1/system/dict-types/${record.id}/items`, {
            method: 'GET',
            ...API_OPTS.NO_REDIRECT,
          }),
        ]);
        setTypeDetail(detailResult);
        setTypeItems(dictItems);
      } finally {
        typeCrud.detail.setLoading(false);
      }
    },
    [setTypeDetail, setTypeItems, typeCrud.detail],
  );

  const closeDetail = useCallback(() => {
    typeCrud.detail.close();
    setTypeDetail(null);
    setTypeItems([]);
  }, [setTypeDetail, setTypeItems, typeCrud.detail]);

  const openCreateType = useCallback(() => {
    typeCrud.drawer.openCreate();
    typeForm.resetFields();
    typeForm.setFieldsValue({ status: 'ENABLED' });
  }, [typeCrud.drawer, typeForm]);

  const openEditType = useCallback(
    async (record: DictTypeRecord) => {
      typeCrud.drawer.openEdit(record, record.id);
      const detailResult = await request<DictTypeRecord>(`/v1/system/dict-types/${record.id}`, {
        method: 'GET',
        ...API_OPTS.NO_REDIRECT,
      });
      typeForm.setFieldsValue({
        dictCode: detailResult.dictCode,
        dictName: detailResult.dictName,
        status: detailResult.status,
        remark: detailResult.remark ?? undefined,
      });
    },
    [typeCrud.drawer, typeForm],
  );

  const saveType = useCallback(async () => {
    setTypeSaving(true);
    try {
      const values = await typeForm.validateFields();
      if (typeCrud.drawer.editingId) {
        await request<DictTypeRecord>(`/v1/system/dict-types/${typeCrud.drawer.editingId}`, {
          method: 'PUT',
          data: values,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success('字典类型已更新');
      } else {
        await request<DictTypeRecord>('/v1/system/dict-types', {
          method: 'POST',
          data: values,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success('字典类型已创建');
      }
      typeCrud.drawer.close();
      typeCrud.reloadTable();
    } finally {
      setTypeSaving(false);
    }
  }, [setTypeSaving, typeCrud, typeForm]);

  const deleteType = useCallback(
    (record: DictTypeRecord) => {
      confirmAction({
        title: t('ui.settings.dicts.deleteDictionaryType'),
        content: t('ui.settings.dicts.deleteDictionaryTypeAssociatedItemsWillAlsoBe', { dictName: record.dictName }),
        okText: t('ui.settings.dicts.delete.0ad952f3'),
        okButtonProps: { danger: true },
        onOk: async () => {
          await request<boolean>(`/v1/system/dict-types/${record.id}`, {
            method: 'DELETE',
            ...API_OPTS.NO_REDIRECT,
          });
          message.success('字典类型已删除');
          typeCrud.reloadTable();
        },
      });
    },
    [typeCrud],
  );
  const typeColumns = useMemo(
    () =>
      buildDictTypeColumns({
        isMobile: responsive.isMobile,
        buildRowActions: actionPermission.buildTableActions,
        onOpenDetail: (record) => void openDetail(record),
        onOpenEdit: (record) => void openEditType(record),
        onDelete: deleteType,
      }),
    [actionPermission.buildTableActions, deleteType, openDetail, openEditType, responsive.isMobile],
  );
  const saving = useMemo(() => typeSaving || itemSaving, [itemSaving, typeSaving]);

  return {
    actionPermission,
    responsive,
    searchConfig,
    buildToolbarButtons,
    typeCrud,
    typeForm,
    typeDetail,
    setTypeDetail,
    items: typeItems,
    typeItems,
    setTypeItems,
    typeSaving,
    setTypeSaving,
    canSaveType,
    typeFormProps,
    detailProps,
    refreshDictItems,
    openCreateType,
    openEditType,
    saveType,
    deleteType,
    closeDetail,
    typeColumns,
    itemDrawer,
    itemForm,
    itemSaving,
    setItemSaving,
    canSaveItem,
    itemFormProps,
    openCreateItem,
    saveItem,
    dictItemColumns,
    saving,
  };
};

const DictManagementPage = () => {
  const {
    typeCrud,
    itemDrawer,
    typeDetail,
    items,
    saving,
    canSaveType,
    canSaveItem,
    searchConfig,
    buildToolbarButtons,
    responsive,
    typeColumns,
    dictItemColumns,
    openCreateType,
    openCreateItem,
    saveType,
    saveItem,
    closeDetail,
    typeFormProps,
    itemFormProps,
    detailProps,
  } = useDictManagement();

  const typeTableRequest = useMemo(
    () => buildTableRequest((params) =>
      request<PagedResponse<DictTypeRecord>>('/v1/system/dict-types', {
        method: 'GET',
        params,
        ...API_OPTS.NO_REDIRECT,
      }),
    ),
    [],
  );

  return (
    <ManagementPage title={t('ui.settings.dicts.dictionaryManagement')}>
      <ManagementPageBody>
        <ManagementTable<DictTypeRecord>
          actionRef={typeCrud.actionRef}
          rowKey="id"
          columns={typeColumns}
          isMobile={responsive.isMobile}
          search={searchConfig}
          request={typeTableRequest as ManagementTableProps<DictTypeRecord>['request']}
          toolBarRender={() =>
            buildToolbarButtons([
              {
                key: 'create',
                permission: 'system:dict:create',
                type: 'primary',
                label: t('ui.settings.dicts.addDictionaryType'),
                onClick: openCreateType,
              },
              {
                key: 'refresh',
                label: t('ui.settings.dicts.refresh'),
                onClick: typeCrud.reloadTable,
              },
            ])
          }
        />
      </ManagementPageBody>

      <DictManagementDrawers
        typeDrawerOpen={typeCrud.drawer.open}
        typeDrawerTitle={typeCrud.drawer.editingId ? t('ui.settings.dicts.editDictionaryType') : t('ui.settings.dicts.addDictionaryType')}
        typeDrawerSaving={saving}
        canSaveType={canSaveType}
        typeFormProps={typeFormProps}
        onCloseTypeDrawer={typeCrud.drawer.close}
        onSaveType={() => void saveType()}
        typeDetailDrawerOpen={typeCrud.detail.open}
        typeDetailDrawerTitle={typeCrud.detail.currentRecord ? `${t('ui.settings.dicts.dictionaryDetails')} · ${typeCrud.detail.currentRecord.dictName}` : t('ui.settings.dicts.dictionaryDetails')}
        typeDetailLoading={typeCrud.detail.loading}
        typeDetail={typeDetail}
        detailProps={detailProps}
        itemColumns={dictItemColumns}
        itemRows={items}
        isMobile={responsive.isMobile}
        searchConfig={searchConfig}
        buildToolbarButtons={buildToolbarButtons}
        onCreateItem={openCreateItem}
        onCloseDetailDrawer={closeDetail}
        itemDrawerOpen={itemDrawer.open}
        itemDrawerTitle={itemDrawer.editingId ? t('ui.settings.dicts.editDictionaryItem') : t('ui.settings.dicts.addDictionaryItem')}
        itemDrawerSaving={saving}
        canSaveItem={canSaveItem}
        itemFormProps={itemFormProps}
        onCloseItemDrawer={itemDrawer.close}
        onSaveItem={() => void saveItem()}
      />
    </ManagementPage>
  );
};

export default DictManagementPage;
