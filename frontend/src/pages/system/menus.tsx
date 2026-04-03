import { useMemo, useState } from 'react';
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button, Card, Descriptions, Drawer, Form, Input, InputNumber, Select, Space, Tag, message } from 'antd';
import { useRequest } from 'umi';
import { iamService } from '@/services/iam';
import type { MenuRecord } from '@/types/api';
import { usePermission } from '@/hooks/usePermission';

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
  const { canAccess } = usePermission();
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

  const columns = useMemo<ProColumns<MenuRecord>[]>(
    () => [
      { title: '菜单编码', dataIndex: 'menuCode' },
      { title: '菜单名称', dataIndex: 'menuName' },
      { title: '类型', dataIndex: 'menuType' },
      { title: '路由', dataIndex: 'path' },
      { title: '组件', dataIndex: 'component' },
      {
        title: '权限',
        dataIndex: 'permissionKey',
        render: (_, record) => record.permissionKey || '-',
      },
      {
        title: '状态',
        dataIndex: 'status',
        render: (_, record) => <Tag color={record.status === 'ENABLED' ? 'green' : 'default'}>{record.status}</Tag>,
      },
      {
        title: '操作',
        render: (_, record) => (
          <Space wrap>
            {canAccess('system:menu:view') ? (
              <Button
                onClick={() => {
                  setSelectedMenu(record);
                  setDetailOpen(true);
                }}
              >
                详情
              </Button>
            ) : null}
            {canAccess('system:menu:update') ? (
              <Button
                onClick={() => {
                  setSelectedMenu(record);
                  setEditingId(record.id);
                  setEditorOpen(true);
                }}
              >
                编辑
              </Button>
            ) : null}
            {canAccess('system:menu:status') ? (
              <Button
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
              </Button>
            ) : null}
          </Space>
        ),
      },
    ],
    [canAccess],
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
    <PageContainer
      className="saas-management-page saas-crud-page"
      ghost
      breadcrumbRender={false}
      title="菜单管理"
      subTitle="支持菜单树、路由、组件、权限标识和启停状态维护。"
      style={{ height: '100%', minHeight: 0 }}
      content={null}
    >
      <div className="saas-management-page-body">
        <Card className="saas-query-panel">
          <Form form={queryForm} layout="vertical" onFinish={submitQuery} onReset={resetQuery}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 16 }}>
              <Form.Item name="menuCode" label="菜单编码">
                <Input allowClear placeholder="输入菜单编码" />
              </Form.Item>
              <Form.Item name="keyword" label="菜单名称">
                <Input allowClear placeholder="输入菜单名称" />
              </Form.Item>
              <Form.Item name="permissionKey" label="权限标识">
                <Input allowClear placeholder="输入权限标识" />
              </Form.Item>
            </div>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button htmlType="reset">重置</Button>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
              <Button onClick={() => menuQuery.refresh()}>刷新</Button>
            </Space>
          </Form>
        </Card>

        <Card className="saas-action-bar">
          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Space>
              {canAccess('system:menu:create') ? (
                <Button type="primary" onClick={openCreate}>
                  新增菜单
                </Button>
              ) : null}
            </Space>
            <Button onClick={() => menuQuery.refresh()}>刷新列表</Button>
          </Space>
        </Card>

        <Card className="saas-crud-table-card" bodyStyle={{ minHeight: 0 }}>
          <ProTable<MenuRecord>
            rowKey="id"
            columns={columns}
            dataSource={filteredMenus}
            loading={menuQuery.loading}
            search={false}
            options={false}
            toolBarRender={false}
            pagination={false}
            expandable={{ defaultExpandAllRows: true }}
          />
        </Card>

        <Drawer
          className="saas-detail-drawer"
          title={editingId ? '编辑菜单' : '新增菜单'}
          open={editorOpen}
          onClose={() => setEditorOpen(false)}
          width={720}
          destroyOnClose
          extra={
            <Space>
              <Button onClick={() => setEditorOpen(false)}>取消</Button>
              <Button type="primary" onClick={saveMenu}>
                保存
              </Button>
            </Space>
          }
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
        </Drawer>

        <Drawer
          className="saas-detail-drawer"
          title={selectedMenu ? `菜单详情 · ${selectedMenu.menuName}` : '菜单详情'}
          open={detailOpen}
          onClose={() => setDetailOpen(false)}
          width={720}
          destroyOnClose
        >
          {selectedMenu ? (
            <Descriptions
              bordered
              size="small"
              column={2}
              items={[
                { key: 'menuCode', label: '菜单编码', children: selectedMenu.menuCode },
                { key: 'menuName', label: '菜单名称', children: selectedMenu.menuName },
                { key: 'menuType', label: '菜单类型', children: selectedMenu.menuType },
                { key: 'path', label: '路由', children: selectedMenu.path || '-' },
                { key: 'component', label: '组件', children: selectedMenu.component || '-' },
                { key: 'permissionKey', label: '权限标识', children: selectedMenu.permissionKey || '-' },
                { key: 'status', label: '状态', children: selectedMenu.status },
              ]}
            />
          ) : null}
        </Drawer>
      </div>
    </PageContainer>
  );
};
