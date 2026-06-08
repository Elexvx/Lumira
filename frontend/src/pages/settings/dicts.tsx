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
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const dictStatusLabelMap: Record<string, string> = {
  ENABLED: t('启用', 'Enabled'),
  DISABLED: t('停用', 'Disabled'),
};

const renderStatusLabel = (status?: string | null) => dictStatusLabelMap[status || ''] || status || '-';

const buildDictItemColumns = ({
  isDesktop,
  isMobile,
  buildRowActions,
  onOpenEdit,
  onDelete,
}: {
  isDesktop: boolean;
  isMobile: boolean;
  buildRowActions: (items: PermissionAwareTableAction[]) => TableActionItem[];
  onOpenEdit: (record: DictItemRecord) => void;
  onDelete: (record: DictItemRecord) => void;
}): ProColumns<DictItemRecord>[] => [
  { title: t('标签', 'Label'), dataIndex: 'itemLabel' },
  { title: t('值', 'Value'), dataIndex: 'itemValue' },
  { title: t('排序', 'Sort order'), dataIndex: 'sortNo', search: false, responsive: ['md', 'lg', 'xl', 'xxl'] },
  {
    title: t('状态', 'Status'),
    dataIndex: 'status',
    search: false,
    render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{renderStatusLabel(record.status)}</Tag>,
  },
  {
    title: t('备注', 'Remark'),
    dataIndex: 'remark',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_, record) =>
      record.remark ? (
        <Typography.Text copyable={{ text: record.remark }} ellipsis={{ tooltip: record.remark }}>
          {record.remark}
        </Typography.Text>
      ) : (
        '-'
      ),
  },
  {
    title: t('操作', 'Actions'),
    valueType: 'option',
    fixed: isDesktop ? 'right' : undefined,
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={buildRowActions([
          {
            key: 'edit',
            label: t('编辑', 'Edit'),
            permission: 'system:dict:update',
            onClick: () => onOpenEdit(record),
          },
          {
            key: 'delete',
            label: t('删除', 'Delete'),
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
  isDesktop,
  isMobile,
  buildRowActions,
  onOpenDetail,
  onOpenEdit,
  onDelete,
}: {
  isDesktop: boolean;
  isMobile: boolean;
  buildRowActions: (items: PermissionAwareTableAction[]) => TableActionItem[];
  onOpenDetail: (record: DictTypeRecord) => void;
  onOpenEdit: (record: DictTypeRecord) => void;
  onDelete: (record: DictTypeRecord) => void;
}): ProColumns<DictTypeRecord>[] => [
  { title: t('字典编码', 'Dictionary code'), dataIndex: 'dictCode', search: true },
  { title: t('字典名称', 'Dictionary name'), dataIndex: 'dictName', search: true },
  {
    title: t('状态', 'Status'),
    dataIndex: 'status',
    valueEnum: {
      ENABLED: { text: t('启用', 'Enabled'), status: 'Success' },
      DISABLED: { text: t('停用', 'Disabled'), status: 'Default' },
    },
    render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{renderStatusLabel(record.status)}</Tag>,
  },
  {
    title: t('系统内置', 'System built-in'),
    dataIndex: 'isSystem',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    render: (_, record) => <Tag color={Number(record.isSystem) !== 0 ? 'green' : 'default'}>{Number(record.isSystem) !== 0 ? t('是', 'Yes') : t('否', 'No')}</Tag>,
  },
  {
    title: t('备注', 'Remark'),
    dataIndex: 'remark',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    ellipsis: true,
    render: (_, record) =>
      record.remark ? (
        <Typography.Text copyable={{ text: record.remark }} ellipsis={{ tooltip: record.remark }}>
          {record.remark}
        </Typography.Text>
      ) : (
        '-'
      ),
  },
  {
    title: t('操作', 'Actions'),
    valueType: 'option',
    fixed: isDesktop ? 'right' : undefined,
    width: 'var(--saas-spacing-180)',
    render: (_, record) => (
      <TableActionBar
        isMobile={isMobile}
        items={buildRowActions([
          {
            key: 'view',
            label: t('详情', 'Details'),
            permission: 'system:dict:view',
            onClick: () => onOpenDetail(record),
          },
          {
            key: 'edit',
            label: t('编辑', 'Edit'),
            permission: 'system:dict:update',
            onClick: () => onOpenEdit(record),
          },
          {
            key: 'delete',
            label: t('删除', 'Delete'),
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
        message.success(t('字典项已更新', 'Dictionary item updated'));
      } else {
        await request<DictItemRecord>(`/v1/system/dict-types/${dictTypeId}/items`, {
          method: 'POST',
          data: values,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('字典项已创建', 'Dictionary item created'));
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
        title: t('删除字典项', 'Delete dictionary item'),
        content: t(`确认删除字典项「${record.itemLabel}」吗？`, `Delete dictionary item "${record.itemLabel}"?`),
        okText: t('确认删除', 'Delete'),
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
        isDesktop: responsive.isDesktop,
        isMobile: responsive.isMobile,
        buildRowActions: actionPermission.buildTableActions,
        onOpenEdit: openEditItem,
        onDelete: deleteItem,
      }),
    [actionPermission.buildTableActions, deleteItem, openEditItem, responsive.isDesktop, responsive.isMobile],
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
        title: t('删除字典类型', 'Delete dictionary type'),
        content: t(`确认删除字典类型「${record.dictName}」吗？所属字典项会一并停用。`, `Delete dictionary type "${record.dictName}"? Associated items will also be disabled.`),
        okText: t('确认删除', 'Delete'),
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
        isDesktop: responsive.isDesktop,
        isMobile: responsive.isMobile,
        buildRowActions: actionPermission.buildTableActions,
        onOpenDetail: (record) => void openDetail(record),
        onOpenEdit: (record) => void openEditType(record),
        onDelete: deleteType,
      }),
    [actionPermission.buildTableActions, deleteType, openDetail, openEditType, responsive.isDesktop, responsive.isMobile],
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

  const typeTableRequest = buildTableRequest((params) =>
    request<PagedResponse<DictTypeRecord>>('/v1/system/dict-types', {
      method: 'GET',
      params,
      ...API_OPTS.NO_REDIRECT,
    }),
  );

  return (
    <ManagementPage title={t('字典管理', 'Dictionary management')}>
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
                label: t('新增字典类型', 'Add dictionary type'),
                onClick: openCreateType,
              },
              {
                key: 'refresh',
                label: t('刷新', 'Refresh'),
                onClick: typeCrud.reloadTable,
              },
            ])
          }
        />
      </ManagementPageBody>

      <DictManagementDrawers
        typeDrawerOpen={typeCrud.drawer.open}
        typeDrawerTitle={typeCrud.drawer.editingId ? t('编辑字典类型', 'Edit dictionary type') : t('新增字典类型', 'Add dictionary type')}
        typeDrawerSaving={saving}
        canSaveType={canSaveType}
        typeFormProps={typeFormProps}
        onCloseTypeDrawer={typeCrud.drawer.close}
        onSaveType={() => void saveType()}
        typeDetailDrawerOpen={typeCrud.detail.open}
        typeDetailDrawerTitle={typeCrud.detail.currentRecord ? `${t('字典详情', 'Dictionary details')} · ${typeCrud.detail.currentRecord.dictName}` : t('字典详情', 'Dictionary details')}
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
        itemDrawerTitle={itemDrawer.editingId ? t('编辑字典项', 'Edit dictionary item') : t('新增字典项', 'Add dictionary item')}
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
