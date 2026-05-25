import { Button, Checkbox, Empty, Form, Input, Space, Spin, Tag, Tree } from 'antd';
import type { TreeProps } from 'antd';
import type { PermissionActionRecord, PermissionTreeRecord } from '@/types/api';
import type { NormalizedPermissionTreeRecord } from '@/pages/system/rolesPermissionTree';

interface RolePermissionEditorProps {
  permissionTree: PermissionTreeRecord[];
  permissionTreeLoading: boolean;
  editorLoading: boolean;
  pageTreeData: NormalizedPermissionTreeRecord[];
  selectedPageNodeKeys: string[];
  selectedPageCount: number;
  totalPageCount: number;
  activePageKey: string | null;
  activePageNode: NormalizedPermissionTreeRecord | null;
  activePageActionPermissions: PermissionActionRecord[];
  activePageSelectedActionKeys: string[];
  isActivePageSelected: boolean;
  expandedKeys: string[];
  onExpandChange: (keys: string[]) => void;
  onExpandToggle: () => void;
  onSelectAllPages: () => void;
  onPageTreeCheck: (keys: string[]) => void;
  onActivePageChange: (pageKey: string | null) => void;
  onActionPermissionsChange: (keys: string[]) => void;
}

export const RolePermissionEditor = ({
  permissionTree,
  permissionTreeLoading,
  editorLoading,
  pageTreeData,
  selectedPageNodeKeys,
  selectedPageCount,
  totalPageCount,
  activePageKey,
  activePageNode,
  activePageActionPermissions,
  activePageSelectedActionKeys,
  isActivePageSelected,
  expandedKeys,
  onExpandChange,
  onExpandToggle,
  onSelectAllPages,
  onPageTreeCheck,
  onActivePageChange,
  onActionPermissionsChange,
}: RolePermissionEditorProps) => {
  if (editorLoading) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: 420 }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <>
      <Form.Item name="permissionKeys" hidden>
        <Input />
      </Form.Item>
      <div className="role-editor-grid">
        <section className="role-editor-section">
          <div className="role-editor-section__header">
            <div>
              <div className="role-editor-section__title">页面路由权限</div>
              <div className="role-editor-section__meta">先勾选可访问的页面，目录节点仅用于分组，再配置该页面下的按钮权限</div>
            </div>
            <Space>
              <Button size="small" onClick={onExpandToggle}>
                {expandedKeys.length ? '折叠全部' : '展开全部'}
              </Button>
              <Button size="small" onClick={onSelectAllPages}>
                {selectedPageCount === totalPageCount ? '全不选' : '全选'}
              </Button>
            </Space>
          </div>
          <div className="role-permission-tree">
            {permissionTreeLoading ? (
              <div style={{ display: 'grid', placeItems: 'center', minHeight: 320 }}>
                <Spin />
              </div>
            ) : pageTreeData.length ? (
              <Tree
                checkable
                blockNode
                selectable
                treeData={pageTreeData}
                checkedKeys={selectedPageNodeKeys}
                selectedKeys={activePageKey ? [activePageKey] : []}
                expandedKeys={expandedKeys}
                onExpand={(nextExpandedKeys) => onExpandChange(nextExpandedKeys.map(String))}
                onCheck={(checkedKeys: Parameters<NonNullable<TreeProps['onCheck']>>[0], info) => {
                  const nextCheckedKeys = Array.isArray(checkedKeys) ? checkedKeys.map(String) : [];
                  onPageTreeCheck(nextCheckedKeys);
                  if ((info.node as NormalizedPermissionTreeRecord).selectable && (info.node as NormalizedPermissionTreeRecord).pageKey) {
                    onActivePageChange((info.node as NormalizedPermissionTreeRecord).pageKey || null);
                  }
                }}
                onSelect={(_, info) => {
                  if ((info.node as NormalizedPermissionTreeRecord).selectable && (info.node as NormalizedPermissionTreeRecord).pageKey) {
                    onActivePageChange((info.node as NormalizedPermissionTreeRecord).pageKey || null);
                  }
                }}
              />
            ) : (
              <Empty description="暂无可配置页面权限" style={{ padding: '48px 0' }} />
            )}
          </div>
        </section>

        <section className="role-editor-section role-action-panel">
          <div className="role-editor-section__header">
            <div>
              <div className="role-editor-section__title">页面动作权限</div>
              <div className="role-editor-section__meta">按钮权限仅在页面权限勾选后生效</div>
            </div>
          </div>

          {permissionTree.length ? (
            <>
              <div className="role-action-panel__page-name">
                {activePageNode?.pageName || '请从左侧选择页面'}
                {activePageNode?.routeMatched && activePageNode?.routePath ? (
                  <Tag style={{ marginInlineStart: 8 }} color="blue">
                    {activePageNode?.routePath}
                  </Tag>
                ) : activePageNode?.nodeType === 'PAGE' ? (
                  <Tag style={{ marginInlineStart: 8 }} color="red">
                    路由失配
                  </Tag>
                ) : null}
              </div>
              {activePageNode ? (
                activePageActionPermissions.length ? (
                  <Checkbox.Group
                    value={activePageSelectedActionKeys}
                    onChange={(checkedValues) => onActionPermissionsChange(checkedValues.map(String))}
                    className="role-action-grid"
                    disabled={!isActivePageSelected}
                    options={activePageActionPermissions.map((item) => ({
                      label: item.permissionName,
                      value: item.permissionKey,
                    }))}
                  />
                ) : (
                  <div className="role-action-panel__empty">
                    <Empty description="该页面暂无子权限" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  </div>
                )
              ) : (
                <div className="role-action-panel__empty">
                  <Empty description="请从左侧页面权限树中选择一个页面" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                </div>
              )}
            </>
          ) : (
            <div className="role-action-panel__empty">
              <Empty description="请先在上方勾选一个页面" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            </div>
          )}
        </section>
      </div>
    </>
  );
};
