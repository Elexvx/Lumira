import { PageContainer, ProDescriptions, ProTable } from '@ant-design/pro-components';
import { Button, Drawer, Form, Space, Spin, message } from 'antd';
import { useMemo, useState } from 'react';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { useCrudDrawerState } from '@/features/crud/useCrudDrawerState';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { useStandardFormProps } from '@/features/form/config';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { buildMobilePagination, buildTableRequest, buildTableScroll } from '@/features/table/proTable';
import { useResponsive } from '@/hooks/useResponsive';
import { buildDictItemColumns, buildDictTypeColumns, dictTypeDetailColumns } from '@/pages/system/dicts/columns';
import { DictItemForm } from '@/pages/system/dicts/components/DictItemForm';
import { DictTypeForm } from '@/pages/system/dicts/components/DictTypeForm';
import { dictService } from '@/services/dict';
import type { DictItemRecord, DictTypeRecord } from '@/types/api';

const DictManagementPage = () => {
  const typeCrud = useCrudPageState<DictTypeRecord>();
  const itemDrawer = useCrudDrawerState<DictItemRecord>();
  const [typeForm] = Form.useForm();
  const [itemForm] = Form.useForm();
  const actionPermission = useActionPermission();
  const responsive = useResponsive();
  const [typeDetail, setTypeDetail] = useState<DictTypeRecord | null>(null);
  const [items, setItems] = useState<DictItemRecord[]>([]);
  const [saving, setSaving] = useState(false);
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

  const typeColumns = useMemo(
    () =>
      buildDictTypeColumns({
        isDesktop: responsive.isDesktop,
        isMobile: responsive.isMobile,
        buildRowActions: actionPermission.buildTableActions,
        onOpenDetail: (record) => void openDetail(record),
        onOpenEdit: (record) => void openEditType(record),
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
      }),
    [actionPermission.buildTableActions, responsive.isDesktop, responsive.isMobile],
  );

  return (
    <PageContainer title="字典管理" className="saas-management-page">
      <div className="saas-table-wrap">
        <ProTable<DictTypeRecord>
          actionRef={typeCrud.actionRef}
          rowKey="id"
          columns={typeColumns}
          search={{ labelWidth: 'auto', span: responsive.isMobile ? 24 : 8 }}
          options={false}
          pagination={buildMobilePagination({ showSizeChanger: true }, responsive.isMobile)}
          scroll={buildTableScroll(typeColumns, responsive.isMobile)}
          request={buildTableRequest((params) => dictService.types(params, { autoRedirectOnUnauthorized: false }))}
          toolBarRender={() =>
            actionPermission.buildToolbarActions([
              {
                permission: 'system:dict:create',
                value: (
                  <Button key="create" type="primary" size={responsive.isMobile ? 'small' : 'middle'} onClick={openCreateType}>
                    新增字典类型
                  </Button>
                ),
              },
              {
                value: (
                  <Button key="refresh" size={responsive.isMobile ? 'small' : 'middle'} onClick={typeCrud.reloadTable}>
                    刷新
                  </Button>
                ),
              },
            ])
          }
        />
      </div>

      <Drawer
        title={typeCrud.drawer.editingId ? '编辑字典类型' : '新增字典类型'}
        open={typeCrud.drawer.open}
        onClose={typeCrud.drawer.close}
        width={720}
        destroyOnClose
        footer={
          <div className="saas-drawer-footer">
            <Space>
              <Button onClick={typeCrud.drawer.close}>取消</Button>
              <Button type="primary" loading={saving} onClick={() => void saveType()}>
                保存
              </Button>
            </Space>
          </div>
        }
      >
        <DictTypeForm formProps={typeFormProps} />
      </Drawer>

      <Drawer
        title={typeCrud.detail.currentRecord ? `字典详情 · ${typeCrud.detail.currentRecord.dictName}` : '字典详情'}
        open={typeCrud.detail.open}
        onClose={() => {
          typeCrud.detail.close();
          setTypeDetail(null);
          setItems([]);
        }}
        width={900}
        destroyOnClose
      >
        {typeCrud.detail.loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : typeDetail ? (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <ProDescriptions<DictTypeRecord> {...detailProps} columns={dictTypeDetailColumns} />
            <div className="saas-table-wrap">
              <ProTable<DictItemRecord>
                rowKey="id"
                columns={dictItemColumns}
                dataSource={items}
                search={false}
                options={false}
                pagination={false}
                scroll={buildTableScroll(dictItemColumns, responsive.isMobile)}
                toolBarRender={() =>
                  actionPermission.buildToolbarActions([
                    {
                      permission: 'system:dict:update',
                      value: (
                        <Button key="create" type="primary" size={responsive.isMobile ? 'small' : 'middle'} onClick={openCreateItem}>
                          新增项
                        </Button>
                      ),
                    },
                  ])
                }
              />
            </div>
          </Space>
        ) : null}
      </Drawer>

      <Drawer
        title={itemDrawer.editingId ? '编辑字典项' : '新增字典项'}
        open={itemDrawer.open}
        onClose={itemDrawer.close}
        width={720}
        destroyOnClose
        footer={
          <div className="saas-drawer-footer">
            <Space>
              <Button onClick={itemDrawer.close}>取消</Button>
              <Button type="primary" loading={saving} onClick={() => void saveItem()}>
                保存
              </Button>
            </Space>
          </div>
        }
      >
        <DictItemForm formProps={itemFormProps} />
      </Drawer>
    </PageContainer>
  );
};

export default DictManagementPage;
