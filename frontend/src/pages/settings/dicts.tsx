import { ProDescriptions } from '@ant-design/pro-components';
import { Form, Space, Spin, message } from 'antd';
import { useMemo, useState } from 'react';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { useCrudDrawerState } from '@/features/crud/useCrudDrawerState';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { useStandardFormProps } from '@/features/form/config';
import { ManagementDrawer, ManagementPage, ManagementTable } from '@/features/management';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { buildTableRequest } from '@/features/table/proTable';
import { buildDictItemColumns, buildDictTypeColumns, dictTypeDetailColumns } from '@/pages/settings/dicts/columns';
import { DictItemForm } from '@/pages/settings/dicts/components/DictItemForm';
import { DictTypeForm } from '@/pages/settings/dicts/components/DictTypeForm';
import { dictService } from '@/services/dict';
import type { DictItemRecord, DictTypeRecord } from '@/types/api';
import { confirmAction } from '@/utils/confirm';

const DictManagementPage = () => {
  const typeCrud = useCrudPageState<DictTypeRecord>();
  const itemDrawer = useCrudDrawerState<DictItemRecord>();
  const [typeForm] = Form.useForm();
  const [itemForm] = Form.useForm();
  const { actionPermission, responsive, searchConfig, buildToolbarButtons } = usePagePermissionActions();
  const [typeDetail, setTypeDetail] = useState<DictTypeRecord | null>(null);
  const [items, setItems] = useState<DictItemRecord[]>([]);
  const [saving, setSaving] = useState(false);
  const canSaveType = actionPermission.can(typeCrud.drawer.editingId ? 'system:dict:update' : 'system:dict:create');
  const canSaveItem = actionPermission.can('system:dict:update');
  const typeFormProps = useStandardFormProps({
    form: typeForm,
    initialValues: { status: 'ENABLED' },
  });
  const itemFormProps = useStandardFormProps({
    form: itemForm,
    initialValues: { sortNo: 0, status: 'ENABLED' },
  });
  const detailProps = useDetailProDescriptionsProps<DictTypeRecord>({
    column: responsive.isMobile ? 1 : 2,
    dataSource: typeDetail || undefined,
  });

  const refreshDictItems = async (dictTypeId: number) => {
    const dictItems = await dictService.items(dictTypeId, { autoRedirectOnUnauthorized: false });
    setItems(dictItems);
  };

  const openCreateType = () => {
    typeCrud.drawer.openCreate();
    typeForm.resetFields();
    typeForm.setFieldsValue({ status: 'ENABLED' });
  };

  const openEditType = async (record: DictTypeRecord) => {
    typeCrud.drawer.openEdit(record, record.id);
    const detailResult = await dictService.typeDetail(record.id, { autoRedirectOnUnauthorized: false });
    typeForm.setFieldsValue(detailResult);
  };

  const openDetail = async (record: DictTypeRecord) => {
    typeCrud.detail.openDetail(record);
    typeCrud.detail.setLoading(true);
    try {
      const [detailResult, dictItems] = await Promise.all([
        dictService.typeDetail(record.id, { autoRedirectOnUnauthorized: false }),
        dictService.items(record.id, { autoRedirectOnUnauthorized: false }),
      ]);
      setTypeDetail(detailResult);
      setItems(dictItems);
    } finally {
      typeCrud.detail.setLoading(false);
    }
  };

  const saveType = async () => {
    setSaving(true);
    try {
      const values = await typeForm.validateFields();
      if (typeCrud.drawer.editingId) {
        await dictService.updateType(typeCrud.drawer.editingId, values, { autoRedirectOnUnauthorized: false });
        message.success('字典类型已更新');
      } else {
        await dictService.createType(values, { autoRedirectOnUnauthorized: false });
        message.success('字典类型已创建');
      }
      typeCrud.drawer.close();
      typeCrud.reloadTable();
    } finally {
      setSaving(false);
    }
  };

  const deleteType = (record: DictTypeRecord) => {
    confirmAction({
      title: '删除字典类型',
      content: `确认删除字典类型「${record.dictName}」吗？所属字典项会一并停用。`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await dictService.deleteType(record.id, { autoRedirectOnUnauthorized: false });
        message.success('字典类型已删除');
        typeCrud.reloadTable();
      },
    });
  };

  const openCreateItem = () => {
    itemDrawer.openCreate();
    itemForm.resetFields();
    itemForm.setFieldsValue({ sortNo: 0, status: 'ENABLED' });
  };

  const openEditItem = (record: DictItemRecord) => {
    itemDrawer.openEdit(record, record.id);
    itemForm.setFieldsValue(record);
  };

  const saveItem = async () => {
    const dictTypeId = typeDetail?.id;
    if (!dictTypeId) {
      return;
    }
    setSaving(true);
    try {
      const values = await itemForm.validateFields();
      if (itemDrawer.editingId) {
        await dictService.updateItem(dictTypeId, itemDrawer.editingId, values, { autoRedirectOnUnauthorized: false });
        message.success('字典项已更新');
      } else {
        await dictService.createItem(dictTypeId, values, { autoRedirectOnUnauthorized: false });
        message.success('字典项已创建');
      }
      itemDrawer.close();
      await refreshDictItems(dictTypeId);
      typeCrud.reloadTable();
    } finally {
      setSaving(false);
    }
  };

  const deleteItem = (record: DictItemRecord) => {
    const dictTypeId = typeDetail?.id;
    if (!dictTypeId) {
      return;
    }
    confirmAction({
      title: '删除字典项',
      content: `确认删除字典项「${record.itemLabel}」吗？`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await dictService.deleteItem(dictTypeId, record.id, { autoRedirectOnUnauthorized: false });
        message.success('字典项已删除');
        await refreshDictItems(dictTypeId);
      },
    });
  };

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
    [actionPermission.buildTableActions, responsive.isDesktop, responsive.isMobile],
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
    [actionPermission.buildTableActions, responsive.isDesktop, responsive.isMobile],
  );

  return (
    <ManagementPage title="字典管理">
      <ManagementTable<DictTypeRecord>
          actionRef={typeCrud.actionRef}
          rowKey="id"
          columns={typeColumns}
          isMobile={responsive.isMobile}
          search={searchConfig}
          request={buildTableRequest((params) => dictService.types(params, { autoRedirectOnUnauthorized: false }))}
          toolBarRender={() =>
            buildToolbarButtons([
              {
                key: 'create',
                permission: 'system:dict:create',
                type: 'primary',
                label: '新增字典类型',
                onClick: openCreateType,
              },
              {
                key: 'refresh',
                label: '刷新',
                onClick: typeCrud.reloadTable,
              },
            ])
          }
      />

      <ManagementDrawer
        title={typeCrud.drawer.editingId ? '编辑字典类型' : '新增字典类型'}
        open={typeCrud.drawer.open}
        onClose={typeCrud.drawer.close}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: typeCrud.drawer.close },
          { key: 'save', label: '保存', type: 'primary', loading: saving, disabled: !canSaveType, onClick: () => void saveType() },
        ]}
      >
        <DictTypeForm formProps={typeFormProps} />
      </ManagementDrawer>

      <ManagementDrawer
        title={typeCrud.detail.currentRecord ? `字典详情 · ${typeCrud.detail.currentRecord.dictName}` : '字典详情'}
        open={typeCrud.detail.open}
        onClose={() => {
          typeCrud.detail.close();
          setTypeDetail(null);
          setItems([]);
        }}
      >
        {typeCrud.detail.loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : typeDetail ? (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <ProDescriptions<DictTypeRecord> {...detailProps} columns={dictTypeDetailColumns} />
              <ManagementTable<DictItemRecord>
                rowKey="id"
                columns={dictItemColumns}
                isMobile={responsive.isMobile}
                dataSource={items}
                search={false}
                pagination={false}
                toolBarRender={() =>
                  buildToolbarButtons([
                    {
                      key: 'create-item',
                      permission: 'system:dict:update',
                      type: 'primary',
                      label: '新增项',
                      onClick: openCreateItem,
                    },
                  ])
                }
              />
          </Space>
        ) : null}
      </ManagementDrawer>

      <ManagementDrawer
        title={itemDrawer.editingId ? '编辑字典项' : '新增字典项'}
        open={itemDrawer.open}
        onClose={itemDrawer.close}
        footerActions={[
          { key: 'cancel', label: '取消', onClick: itemDrawer.close },
          { key: 'save', label: '保存', type: 'primary', loading: saving, disabled: !canSaveItem, onClick: () => void saveItem() },
        ]}
      >
        <DictItemForm formProps={itemFormProps} />
      </ManagementDrawer>
    </ManagementPage>
  );
};

export default DictManagementPage;
