import { PageContainer, ProDescriptions, ProTable } from '@ant-design/pro-components';
import { Button, Drawer, Form, Input, Modal, Select, Space, Spin, Tag, message } from 'antd';
import { useMemo, useState } from 'react';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { useDetailFormProps, useDetailProDescriptionsProps } from '@/features/detail/config';
import { useActionPermission } from '@/features/permissions/useActionPermission';
import { buildMobilePagination, buildTableRequest, buildTableScroll } from '@/features/table/proTable';
import { ROLE_TYPE_OPTIONS } from '@/constants/role';
import { useResponsive } from '@/hooks/useResponsive';
import { buildRoleColumns, roleDetailColumns } from '@/pages/system/roles/columns';
import { RolePermissionEditor } from '@/pages/system/roles/components/RolePermissionEditor';
import { useRolePermissionEditor } from '@/pages/system/roles/hooks/useRolePermissionEditor';
import { iamService } from '@/services/iam';
import type { RoleDetail, RoleRecord } from '@/types/api';
import './roles.less';

const RoleManagementPage = () => {
  const roleCrud = useCrudPageState<RoleRecord>();
  const [editorForm] = Form.useForm();
  const actionPermission = useActionPermission();
  const responsive = useResponsive();
  const [selectedRoleDetail, setSelectedRoleDetail] = useState<RoleDetail | null>(null);
  const [editorDirty, setEditorDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const editorFormProps = useDetailFormProps({
    form: editorForm,
    initialValues: { roleType: 'CUSTOM', permissionKeys: [] },
    onValuesChange: () => setEditorDirty(true),
    className: 'role-editor-form',
  });
  const detailProps = useDetailProDescriptionsProps<RoleDetail>({
    column: responsive.isMobile ? 1 : 2,
    dataSource: selectedRoleDetail || undefined,
  });
  const permissionEditor = useRolePermissionEditor({
    form: editorForm,
    editorOpen: roleCrud.drawer.open,
    onDirty: () => setEditorDirty(true),
  });

  const closeEditorDrawer = () => {
    roleCrud.drawer.close();
    permissionEditor.setEditorLoading(false);
    setEditorDirty(false);
    permissionEditor.resetEditorPermissionState();
  };

  const openCreate = () => {
    roleCrud.drawer.openCreate();
    permissionEditor.resetEditorPermissionState();
    setEditorDirty(false);
    permissionEditor.setEditorLoading(false);
    editorForm.resetFields();
    editorForm.setFieldsValue({ roleType: 'CUSTOM', permissionKeys: [] });
  };

  const openEdit = async (record: RoleRecord) => {
    roleCrud.drawer.openEdit(record, record.id);
    permissionEditor.resetEditorPermissionState();
    permissionEditor.setEditorLoading(true);
    setEditorDirty(false);

    try {
      const detail = await iamService.roleDetail(record.id, { autoRedirectOnUnauthorized: false });
      editorForm.setFieldsValue({
        ...detail,
        permissionKeys: detail.permissionKeys || [],
      });
      permissionEditor.syncActivePageByPermissionKeys(detail.permissionKeys || []);
    } catch {
      message.error('加载角色信息失败，请稍后重试');
      roleCrud.drawer.close();
    } finally {
      permissionEditor.setEditorLoading(false);
    }
  };

  const openDetail = async (record: RoleRecord) => {
    roleCrud.detail.openDetail(record);
    roleCrud.detail.setLoading(true);
    try {
      const detail = await iamService.roleDetail(record.id, { autoRedirectOnUnauthorized: false });
      setSelectedRoleDetail(detail);
    } finally {
      roleCrud.detail.setLoading(false);
    }
  };

  const saveRole = async () => {
    setSaving(true);
    try {
      const values = await editorForm.validateFields();
      const payload = {
        ...values,
        permissionKeys: values.permissionKeys || [],
      };
      if (roleCrud.drawer.editingId) {
        await iamService.updateRole(roleCrud.drawer.editingId, payload, { autoRedirectOnUnauthorized: false });
        message.success('角色已更新');
      } else {
        await iamService.createRole(payload, { autoRedirectOnUnauthorized: false });
        message.success('角色已创建');
      }
      closeEditorDrawer();
      roleCrud.reloadTable();
    } finally {
      setSaving(false);
    }
  };

  const handleEditorClose = () => {
    if (!editorDirty) {
      closeEditorDrawer();
      return;
    }

    Modal.confirm({
      title: '提示',
      content: '关闭抽屉将丢失未保存的内容，是否确认关闭？',
      okText: '继续编辑',
      cancelText: '确认关闭',
      centered: true,
      onOk: () => Promise.resolve(),
      onCancel: closeEditorDrawer,
    });
  };

  const columns = useMemo(
    () =>
      buildRoleColumns({
        isDesktop: responsive.isDesktop,
        isMobile: responsive.isMobile,
        buildRowActions: actionPermission.buildTableActions,
        onOpenDetail: (record) => void openDetail(record),
        onOpenEdit: (record) => void openEdit(record),
      }),
    [actionPermission.buildTableActions, responsive.isDesktop, responsive.isMobile],
  );

  return (
    <PageContainer title="角色管理" className="saas-management-page">
      <div className="saas-table-wrap">
        <ProTable<RoleRecord>
          actionRef={roleCrud.actionRef}
          rowKey="id"
          columns={columns}
          search={{ labelWidth: 'auto', span: responsive.isMobile ? 24 : 8 }}
          options={false}
          pagination={buildMobilePagination({ showSizeChanger: true }, responsive.isMobile)}
          scroll={buildTableScroll(columns, responsive.isMobile)}
          request={buildTableRequest((params) => iamService.roles(params, { autoRedirectOnUnauthorized: false }))}
          toolBarRender={() =>
            actionPermission.buildToolbarActions([
              {
                permission: 'system:role:create',
                value: (
                  <Button key="create" type="primary" size={responsive.isMobile ? 'small' : 'middle'} onClick={openCreate}>
                    新增角色
                  </Button>
                ),
              },
              {
                value: (
                  <Button key="refresh" size={responsive.isMobile ? 'small' : 'middle'} onClick={roleCrud.reloadTable}>
                    刷新
                  </Button>
                ),
              },
            ])
          }
        />
      </div>

      <Drawer
        title={roleCrud.drawer.editingId ? '编辑角色 / 分配权限' : '新增角色'}
        open={roleCrud.drawer.open}
        onClose={handleEditorClose}
        width="min(1040px, 96vw)"
        destroyOnClose
        footer={
          <div className="saas-drawer-footer">
            <Space>
              <Button onClick={handleEditorClose}>取消</Button>
              <Button type="primary" loading={saving} onClick={() => void saveRole()}>
                保存
              </Button>
            </Space>
          </div>
        }
      >
        <Form {...editorFormProps}>
          <Form.Item name="roleCode" label="角色编码" rules={[{ required: true, message: '请输入角色编码' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="roleName" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="roleType" label="角色类型" rules={[{ required: true, message: '请选择角色类型' }]}>
            <Select options={ROLE_TYPE_OPTIONS as unknown as { label: string; value: string }[]} />
          </Form.Item>
          <RolePermissionEditor
            permissionTree={permissionEditor.permissionTree}
            permissionTreeLoading={permissionEditor.permissionTreeLoading}
            editorLoading={permissionEditor.editorLoading}
            pageTreeData={permissionEditor.pageTreeData}
            selectedPageNodeKeys={permissionEditor.selectedPageNodeKeys}
            selectedPageCount={permissionEditor.selectedPageCount}
            totalPageCount={permissionEditor.totalPageCount}
            activePageKey={permissionEditor.activePageKey}
            activePageNode={permissionEditor.activePageNode}
            activePageActionPermissions={permissionEditor.activePageActionPermissions}
            activePageSelectedActionKeys={permissionEditor.activePageSelectedActionKeys}
            isActivePageSelected={permissionEditor.isActivePageSelected}
            expandedKeys={permissionEditor.expandedKeys}
            onExpandChange={permissionEditor.setExpandedKeys}
            onExpandToggle={permissionEditor.handleExpandToggle}
            onSelectAllPages={permissionEditor.handleSelectAllPages}
            onPageTreeCheck={permissionEditor.handlePageTreeCheck}
            onActivePageChange={permissionEditor.setActivePageKey}
            onActionPermissionsChange={permissionEditor.handleActionPermissionsChange}
          />
        </Form>
      </Drawer>

      <Drawer
        title={roleCrud.detail.currentRecord ? `角色详情 · ${roleCrud.detail.currentRecord.roleName}` : '角色详情'}
        open={roleCrud.detail.open}
        onClose={() => {
          roleCrud.detail.close();
          setSelectedRoleDetail(null);
        }}
        width={720}
        destroyOnClose
      >
        {roleCrud.detail.loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 240 }}>
            <Spin />
          </div>
        ) : selectedRoleDetail ? (
          <>
            <ProDescriptions<RoleDetail> {...detailProps} columns={roleDetailColumns} />
            <div style={{ marginTop: 16 }}>
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                <div>当前权限</div>
                {selectedRoleDetail.permissionKeys?.length ? (
                  <Space wrap>
                    {selectedRoleDetail.permissionKeys.map((item) => (
                      <Tag key={item} color="geekblue">
                        {item}
                      </Tag>
                    ))}
                  </Space>
                ) : (
                  <Tag>暂无权限</Tag>
                )}
              </Space>
            </div>
          </>
        ) : null}
      </Drawer>
    </PageContainer>
  );
};

export default RoleManagementPage;
