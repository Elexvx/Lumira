import { useMemo, useState } from 'react';
import { Button, Card, Form, Input, InputNumber, Modal, Select, Space, Tag, message } from 'antd';
import { useRequest } from 'umi';
import { ManagementPageContainer } from '@/components/ManagementPageContainer';
import { QueryPanel } from '@/components/QueryPanel';
import { ActionBar } from '@/components/ActionBar';
import { DataTable } from '@/components/DataTable';
import { DetailDrawer } from '@/components/DetailDrawer';
import { PermissionButton } from '@/components/PermissionButton';
import { iamService } from '@/services/iam';
import type { MenuRecord } from '@/types/api';
import { useResponsive } from '@/hooks/useResponsive';

const flattenMenus = (menus: MenuRecord[], level = 0, result: Array<MenuRecord & { level: number }> = []) => {
  menus.forEach((menu) => {
    result.push({ ...menu, level });
    if (menu.children?.length) {
      flattenMenus(menu.children, level + 1, result);
    }
  });
  return result;
};

export default () => {
  const [queryForm] = Form.useForm();
  const [editorForm] = Form.useForm();
  const { isMobile } = useResponsive();
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [selectedMenu, setSelectedMenu] = useState<MenuRecord | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const menuQuery = useRequest(async () => ({
    data: await iamService.menus({ autoRedirectOnUnauthorized: false }),
  }) as { data: MenuRecord[] });

  const menuTree = menuQuery.data || [];
  const flatMenus = useMemo(() => flattenMenus(menuTree), [menuTree]);

  const filteredMenus = useMemo(() => {
    const keyword = String(query.keyword || '').trim().toLowerCase();
    const menuCode = String(query.menuCode || '').trim().toLowerCase();
    const permissionKey = String(query.permissionKey || '').trim().toLowerCase();
    if (!keyword && !menuCode && !permissionKey) {
      return menuTree;
    }
    const filterTree = (menus: MenuRecord[]): MenuRecord[] =>
      menus
        .map((menu) => {
          const matched =
            (!keyword || menu.menuName.toLowerCase().includes(keyword)) &&
            (!menuCode || menu.menuCode.toLowerCase().includes(menuCode)) &&
            (!permissionKey || (menu.permissionKey || '').toLowerCase().includes(permissionKey));
          const children = menu.children ? filterTree(menu.children) : [];
          if (matched || children.length) {
            return { ...menu, children };
          }
          return null;
        })
        .filter(Boolean) as MenuRecord[];
    return filterTree(menuTree);
  }, [menuTree, query]);

  const columns = useMemo(
    () => [
      { title: '菜单编码', dataIndex: 'menuCode' },
      { title: '菜单名称', dataIndex: 'menuName' },
      { title: '类型', dataIndex: 'menuType' },
      { title: '路由', dataIndex: 'path' },
      { title: '组件', dataIndex: 'component' },
      {
        title: '权限',
        dataIndex: 'permissionKey',
        render: (value: string) => value || '-',
      },
      {
        title: '状态',
        dataIndex: 'status',
        render: (value: string) => <Tag color={value === 'ENABLED' ? 'green' : 'default'}>{value}</Tag>,
      },
      {
        title: '操作',
        render: (_: unknown, record: MenuRecord) => (
          <Space wrap>
            <PermissionButton
              permission="system:menu:view"
              onClick={() => {
                setSelectedMenu(record);
                setDetailOpen(true);
              }}
            >
              详情
            </PermissionButton>
            <PermissionButton
              permission="system:menu:update"
              onClick={() => {
                setSelectedMenu(record);
                setEditingId(record.id);
                setEditorOpen(true);
              }}
            >
              编辑
            </PermissionButton>
            <PermissionButton
              permission="system:menu:status"
              onClick={async () => {
                await iamService.changeMenuStatus(
                  record.id,
                  record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED',
                  { autoRedirectOnUnauthorized: false },
                );
                message.success('状态已更新');
                await menuQuery.refresh();
              }}
            >
              {record.status === 'ENABLED' ? '停用' : '启用'}
            </PermissionButton>
          </Space>
        ),
      },
    ],
    [],
  );

  const submitQuery = async (values: Record<string, unknown>) => setQuery(values);
  const resetQuery = () => {
    queryForm.resetFields();
    setQuery({});
  };

  const openCreate = () => {
    setSelectedMenu(null);
    setEditingId(null);
    editorForm.resetFields();
    editorForm.setFieldsValue({ menuType: 'MENU', status: 'ENABLED', sortNo: 0 });
    setEditorOpen(true);
  };

  const saveMenu = async () => {
    const values = await editorForm.validateFields();
    if (editingId) {
      await iamService.updateMenu(editingId, values, { autoRedirectOnUnauthorized: false });
      message.success('菜单已更新');
    } else {
      await iamService.createMenu(values, { autoRedirectOnUnauthorized: false });
      message.success('菜单已创建');
    }
    setEditorOpen(false);
    await menuQuery.refresh();
  };

  return (
    <ManagementPageContainer title="菜单管理" description="支持菜单树、路由、组件、权限标识和启停状态维护。">
      <QueryPanel
        form={queryForm}
        onSearch={submitQuery}
        onReset={resetQuery}
        columns={isMobile ? 1 : 3}
        collapseCount={3}
        actions={<Button onClick={() => menuQuery.refresh()}>刷新</Button>}
      >
        <Form.Item name="menuCode" label="菜单编码">
          <Input allowClear placeholder="输入菜单编码" />
        </Form.Item>
        <Form.Item name="keyword" label="菜单名称">
          <Input allowClear placeholder="输入菜单名称" />
        </Form.Item>
        <Form.Item name="permissionKey" label="权限标识">
          <Input allowClear placeholder="输入权限标识" />
        </Form.Item>
      </QueryPanel>

      <ActionBar
        left={
          <PermissionButton permission="system:menu:create" type="primary" onClick={openCreate}>
            新增菜单
          </PermissionButton>
        }
        right={<Button onClick={() => menuQuery.refresh()}>刷新列表</Button>}
      />

      <Card bodyStyle={{ height: 520, minHeight: 0 }}>
        <DataTable<MenuRecord & { level?: number }>
          rowKey="id"
          columns={columns}
          dataSource={filteredMenus}
          pagination={false}
          loading={menuQuery.loading}
          middleScroll
          expandable={{ defaultExpandAllRows: true }}
          emptyText="暂无菜单数据"
        />
      </Card>

      <Modal
        open={editorOpen}
        title={editingId ? '编辑菜单' : '新增菜单'}
        onCancel={() => setEditorOpen(false)}
        onOk={saveMenu}
        width={720}
        destroyOnClose
      >
        <Form form={editorForm} layout="vertical" initialValues={{ menuType: 'MENU', status: 'ENABLED', sortNo: 0 }}>
          <Form.Item name="parentId" label="上级菜单">
            <Select
              allowClear
              options={flatMenus.map((menu) => ({
                label: `${'　'.repeat(menu.level || 0)}${menu.menuName}`,
                value: menu.id,
              }))}
            />
          </Form.Item>
          <Form.Item name="menuCode" label="菜单编码" rules={[{ required: true, message: '请输入菜单编码' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="menuName" label="菜单名称" rules={[{ required: true, message: '请输入菜单名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="menuType" label="菜单类型" rules={[{ required: true, message: '请选择菜单类型' }]}>
            <Select
              options={[
                { label: '目录', value: 'CATALOG' },
                { label: '菜单', value: 'MENU' },
                { label: '按钮', value: 'BUTTON' },
              ]}
            />
          </Form.Item>
          <Form.Item name="path" label="路由">
            <Input />
          </Form.Item>
          <Form.Item name="component" label="组件">
            <Input />
          </Form.Item>
          <Form.Item name="icon" label="图标">
            <Input />
          </Form.Item>
          <Form.Item name="sortNo" label="排序">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="permissionKey" label="权限标识">
            <Input />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select options={[{ label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }]} />
          </Form.Item>
        </Form>
      </Modal>

      <DetailDrawer
        title={selectedMenu ? `菜单详情 · ${selectedMenu.menuName}` : '菜单详情'}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        descriptionItems={
          selectedMenu
            ? [
                { key: 'menuCode', label: '菜单编码', children: selectedMenu.menuCode },
                { key: 'menuName', label: '菜单名称', children: selectedMenu.menuName },
                { key: 'menuType', label: '菜单类型', children: selectedMenu.menuType },
                { key: 'path', label: '路由', children: selectedMenu.path || '-' },
                { key: 'component', label: '组件', children: selectedMenu.component || '-' },
                { key: 'permissionKey', label: '权限标识', children: selectedMenu.permissionKey || '-' },
                { key: 'status', label: '状态', children: selectedMenu.status },
              ]
            : undefined
        }
      />
    </ManagementPageContainer>
  );
};
