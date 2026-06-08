import { Form } from 'antd';
import { message } from '@/theme/antdFeedbackBridge';
import { createElement, useCallback, useEffect, useMemo, useState, type ReactElement } from 'react';
import { useDetailProDescriptionsProps } from '@/features/detail/config';
import { useCrudPageState } from '@/features/crud/useCrudPageState';
import { usePagePermissionActions } from '@/features/permissions/usePagePermissionActions';
import { buildTableRequest } from '@/features/table/proTableRequest';
import { TableActionBar, type TableActionItem } from '@/features/table/TableActionBar';
import type { PermissionAwareTableAction } from '@/features/permissions/useActionPermission';
import type { PermissionActionRecord, PermissionRecord, RoleDataScope, RoleDetail, RoleRecord } from '@/types/api';
import { API_OPTS } from '@/utils/errorMessage';
import { ROLE_TYPE_OPTIONS } from '@/constants/role';
import { Space, Tag } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import { useStandardFormProps } from '@/features/form/config';
import { confirmAction } from '@/utils/confirm';
import { normalizePermissionTree } from '@/pages/system/rolesPermissionTree/normalize';
import type { NormalizedPermissionTreeRecord } from '@/pages/system/rolesPermissionTree/normalize';
import type { FormInstance } from 'antd';
import type { PermissionTreeRecord } from '@/types/api';
import { request } from '@/services/common/request';
import type { PagedResult } from '@/types/api';
import { APP_SPACING } from '@/theme/spacing';
import { getLocale } from '@umijs/max';
import { normalizeLocale } from '@/i18n/locale';

const isEnglishLocale = () => normalizeLocale(getLocale()) === 'en-US';
const t = (zh: string, en: string) => (isEnglishLocale() ? en : zh);

const DEFAULT_DATA_SCOPES: RoleDataScope[] = [{ resourceCode: '*', scopeType: 'SELF' }];
const DEFAULT_HOME_PATH = '/dashboard/home';
type RoleEditorMode = 'create' | 'edit' | 'permissions';

type RoleEditorFormValues = Record<string, unknown> & {
  roleCode?: string;
  roleName?: string;
  roleType?: string;
  defaultHomePath?: string;
  permissionKeys?: string[];
  dataScopes?: Array<Partial<import('@/types/api').RoleDataScope>>;
};

type PermissionTreeDataRecord = NormalizedPermissionTreeRecord & {
  key: string;
  title: ReactElement;
  checkable: boolean;
  disableCheckbox: boolean;
};

const walkPermissionTree = (
  nodes: NormalizedPermissionTreeRecord[],
  visit: (node: NormalizedPermissionTreeRecord) => void,
) => {
  nodes.forEach((node) => {
    visit(node);
    if (node.children?.length) {
      walkPermissionTree(node.children, visit);
    }
  });
};

const buildPermissionTreeData = (nodes: NormalizedPermissionTreeRecord[]): PermissionTreeDataRecord[] =>
  nodes.map((node) => ({
    ...node,
    key: node.pageKey,
    checkable: node.nodeType === 'PAGE',
    disableCheckbox: node.nodeType === 'PAGE' ? !node.selectable : !node.children?.length,
    selectable: node.selectable,
    title: createElement(
      'div',
      { className: `role-page-row${node.routeMatched ? '' : ' role-page-row--mismatch'}` },
      createElement('span', { className: 'role-page-row__name' }, node.pageName),
      createElement(
        'span',
        { className: 'role-page-row__meta' },
        node.nodeType === 'CATALOG' ? createElement('span', { className: 'role-page-row__kind' }, t('目录', 'Catalog')) : null,
        node.nodeType === 'PAGE' && node.routePath
          ? createElement(
              'span',
              {
                className: `role-page-row__route${node.routeMatched ? '' : ' role-page-row__route--mismatch'}`,
              },
              node.routePath,
            )
          : null,
        node.nodeType === 'PAGE' && !node.routeMatched
          ? createElement('span', { className: 'role-page-row__hint role-page-row__hint--mismatch' }, t('路由失配', 'Route mismatch'))
          : null,
        node.nodeType === 'CATALOG'
          ? createElement('span', { className: 'role-page-row__hint role-page-row__hint--catalog' }, t('仅作目录分组', 'Catalog grouping only'))
          : null,
      ),
    ),
    children: node.children?.length ? buildPermissionTreeData(node.children) : undefined,
  }));

const collectSelectablePages = (
  nodes: NormalizedPermissionTreeRecord[],
  result: NormalizedPermissionTreeRecord[] = [],
) => {
  walkPermissionTree(nodes, (node) => {
    if (node.nodeType === 'PAGE' && node.selectable && node.permissionKey) {
      result.push(node);
    }
  });
  return result;
};

const collectSelectablePageNodeMap = (
  nodes: NormalizedPermissionTreeRecord[],
  result = new Map<string, NormalizedPermissionTreeRecord>(),
) => {
  walkPermissionTree(nodes, (node) => {
    if (node.nodeType === 'PAGE' && node.selectable && node.permissionKey) {
      result.set(node.pageKey, node);
    }
  });
  return result;
};

const collectPermissionKeyToPageKeyMap = (
  nodes: NormalizedPermissionTreeRecord[],
  result = new Map<string, string[]>(),
) => {
  walkPermissionTree(nodes, (node) => {
    if (node.nodeType === 'PAGE' && node.selectable && node.permissionKey) {
      const pageKeys = result.get(node.permissionKey) ?? [];
      if (!pageKeys.includes(node.pageKey)) {
        pageKeys.push(node.pageKey);
      }
      result.set(node.permissionKey, pageKeys);
    }
  });
  return result;
};

const collectExpandableKeys = (nodes: NormalizedPermissionTreeRecord[], result: string[] = []) => {
  walkPermissionTree(nodes, (node) => {
    if (node.children?.length) {
      result.push(node.pageKey);
    }
  });
  return result;
};

const collectActionPermissionPageMap = (
  nodes: NormalizedPermissionTreeRecord[],
  result = new Map<string, string>(),
) => {
  walkPermissionTree(nodes, (node) => {
    if (node.nodeType === 'PAGE' && node.selectable && node.permissionKey) {
      node.actionPermissions?.forEach((action: PermissionActionRecord) => {
        if (action.permissionKey) {
          result.set(action.permissionKey, node.permissionKey as string);
        }
      });
    }
  });
  return result;
};

const collectAssignablePermissionKeys = (
  nodes: NormalizedPermissionTreeRecord[],
  result = new Set<string>(),
) => {
  walkPermissionTree(nodes, (node) => {
    if (node.nodeType === 'PAGE' && node.selectable && node.permissionKey) {
      result.add(node.permissionKey);
      node.actionPermissions?.forEach((action: PermissionActionRecord) => {
        if (action.permissionKey) {
          result.add(action.permissionKey);
        }
      });
    }
  });
  return result;
};

const normalizePermissionKeysByPages = (
  currentPermissionKeys: string[],
  nextPageKeys: string[],
  allPageKeys: Set<string>,
  actionPermissionPageMap: Map<string, string>,
  assignablePermissionKeys?: Set<string>,
) => {
  const nextPageKeySet = new Set(nextPageKeys);
  const nextPermissionKeys = new Set<string>();

  currentPermissionKeys.forEach((permissionKey) => {
    if (assignablePermissionKeys && !assignablePermissionKeys.has(permissionKey)) {
      return;
    }

    if (nextPageKeySet.has(permissionKey)) {
      nextPermissionKeys.add(permissionKey);
      return;
    }

    const pageKey = actionPermissionPageMap.get(permissionKey);
    if (pageKey) {
      if (nextPageKeySet.has(pageKey)) {
        nextPermissionKeys.add(permissionKey);
      }
      return;
    }

    if (!allPageKeys.has(permissionKey)) {
      nextPermissionKeys.add(permissionKey);
    }
  });

  nextPageKeys.forEach((permissionKey) => {
    nextPermissionKeys.add(permissionKey);
  });

  return Array.from(nextPermissionKeys);
};

const roleTypeValueEnum = ROLE_TYPE_OPTIONS.reduce<Record<string, { text: string }>>((acc, item) => {
  acc[String(item.value)] = { text: item.label };
  return acc;
}, {});

const roleDataColumns: ProColumns<RoleRecord>[] = [
  {
    title: t('角色编码', 'Role code'),
    dataIndex: 'roleCode',
    search: true,
  },
  {
    title: t('角色名称', 'Role name'),
    dataIndex: 'roleName',
    search: true,
    render: (_, record) => (
      <Space size={APP_SPACING.tagWrapGap.desktop[0]} wrap>
        <span>{record.roleName}</span>
        {record.defaultRegistrationRole ? <Tag color="blue">{t('默认注册', 'Default registration')}</Tag> : null}
      </Space>
    ),
  },
  {
    title: t('角色类型', 'Role type'),
    dataIndex: 'roleType',
    valueEnum: roleTypeValueEnum,
    search: {
      transform: (value) => ({ roleType: value }),
    },
  },
  {
    title: t('默认访问页', 'Default landing page'),
    dataIndex: 'defaultHomePath',
    search: false,
    responsive: ['lg', 'xl', 'xxl'],
    render: (_, record) => record.defaultHomePath || '/dashboard/home',
  },
  {
    title: t('权限数', 'Permission count'),
    dataIndex: 'permissionCount',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    render: (_, record) => record.permissionCount ?? 0,
  },
  {
    title: t('用户数', 'User count'),
    dataIndex: 'userCount',
    search: false,
    responsive: ['md', 'lg', 'xl', 'xxl'],
    render: (_, record) => record.userCount ?? 0,
  },
];

const buildRoleActionColumn = ({
  isDesktop,
  isMobile,
  buildRowActions,
  onOpenDetail,
  onOpenEdit,
  onOpenPermissions,
  onDelete,
}: {
  isDesktop: boolean;
  isMobile: boolean;
  buildRowActions: (items: PermissionAwareTableAction[]) => TableActionItem[];
  onOpenDetail: (record: RoleRecord) => void;
  onOpenEdit: (record: RoleRecord) => void;
  onOpenPermissions: (record: RoleRecord) => void;
  onDelete: (record: RoleRecord) => void;
}): ProColumns<RoleRecord> => ({
  title: t('操作', 'Actions'),
  valueType: 'option',
  fixed: isDesktop ? 'right' : undefined,
  width: 'var(--saas-spacing-180)',
  render: (_, record) => (
    <TableActionBar
      isMobile={isMobile}
      items={buildRowActions([
        {
          key: 'detail',
          label: t('详情', 'Details'),
          permission: 'system:role:view',
          onClick: () => onOpenDetail(record),
        },
        {
          key: 'edit',
          label: t('编辑', 'Edit'),
          permission: 'system:role:update',
          onClick: () => onOpenEdit(record),
        },
        {
          key: 'permission',
          label: t('权限分配', 'Permissions'),
          permission: 'system:role:permissions',
          onClick: () => onOpenPermissions(record),
        },
        {
          key: 'delete',
          label: t('删除', 'Delete'),
          permission: 'system:role:delete',
          danger: true,
          disabled: Boolean(record.defaultRegistrationRole) || Number(record.userCount || 0) > 0,
          onClick: () => onDelete(record),
        },
      ])}
    />
  ),
});

const collectDefaultHomeOptions = (nodes: import('@/pages/system/rolesPermissionTree/normalize').NormalizedPermissionTreeRecord[] = []) => {
  const options: { label: string; value: string }[] = [];
  const seen = new Set<string>();
  const walk = (items: import('@/pages/system/rolesPermissionTree/normalize').NormalizedPermissionTreeRecord[]) => {
    items.forEach((item) => {
      if (item.nodeType === 'PAGE' && item.selectable && item.routePath && !seen.has(item.routePath)) {
        seen.add(item.routePath);
        options.push({ label: `${item.pageName}（${item.routePath}）`, value: item.routePath });
      }
      if (item.children?.length) {
        walk(item.children);
      }
    });
  };
  walk(nodes);
  return options;
};

type RolePermissionDisplayItem = {
  permissionKey: string;
  permissionName: string;
  isPagePermission: boolean;
};

type RolePermissionDisplayPage = {
  pageKey: string;
  pageName: string;
  permissionGroup: string;
  routePath?: string;
  permissions: RolePermissionDisplayItem[];
};

const resolvePermissionGroup = (
  nodePermissionGroup: string | undefined,
  permissionKey: string | undefined,
  permissionCatalogMap: Map<string, PermissionRecord>,
) => {
  if (nodePermissionGroup?.trim()) {
    return nodePermissionGroup.trim();
  }
  if (permissionKey && permissionCatalogMap.has(permissionKey)) {
    const catalogGroup = permissionCatalogMap.get(permissionKey)?.permissionGroup?.trim();
    if (catalogGroup) {
      return catalogGroup;
    }
  }
  return permissionKey?.split(':')[0] || 'other';
};

const resolvePermissionName = (
  permissionKey: string,
  fallbackName: string,
  permissionCatalogMap: Map<string, PermissionRecord>,
) => permissionCatalogMap.get(permissionKey)?.permissionName || fallbackName || permissionKey;

const buildRolePermissionDisplayGroups = (
  nodes: NormalizedPermissionTreeRecord[],
  selectedPermissionKeys: string[],
  permissionCatalogMap: Map<string, PermissionRecord> = new Map(),
) => {
  const selectedPermissionKeySet = new Set(selectedPermissionKeys);
  const groupMap = new Map<string, Map<string, RolePermissionDisplayPage>>();
  const seenPermissionKeys = new Set<string>();

  const addPermission = (
    permissionGroup: string,
    pageKey: string,
    pageName: string,
    routePath: string | undefined,
    permissionKey: string,
    permissionName: string,
    isPagePermission: boolean,
  ) => {
    seenPermissionKeys.add(permissionKey);
    const pageMap = groupMap.get(permissionGroup) ?? new Map();
    const page: RolePermissionDisplayPage = pageMap.get(pageKey) ?? {
      pageKey,
      pageName,
      permissionGroup,
      routePath,
      permissions: [],
    };

    if (!page.routePath && routePath) {
      page.routePath = routePath;
    }

    if (!page.permissions.some((item) => item.permissionKey === permissionKey)) {
      page.permissions.push({
        permissionKey,
        permissionName,
        isPagePermission,
      });
    }

    pageMap.set(pageKey, page);
    groupMap.set(permissionGroup, pageMap);
  };

  const visit = (items: NormalizedPermissionTreeRecord[]) => {
    items.forEach((node) => {
      if (node.nodeType === 'PAGE' && node.selectable && node.permissionKey) {
        const groupKey = resolvePermissionGroup(node.permissionGroup, node.permissionKey, permissionCatalogMap);
        const pageName = node.pageName || resolvePermissionName(node.permissionKey, node.pageName || node.permissionKey, permissionCatalogMap);

        if (selectedPermissionKeySet.has(node.permissionKey)) {
          addPermission(
            groupKey,
            node.pageKey,
            pageName,
            node.routePath,
            node.permissionKey,
            resolvePermissionName(node.permissionKey, pageName, permissionCatalogMap),
            true,
          );
        }

        node.actionPermissions?.forEach((action: PermissionActionRecord) => {
          if (!action.permissionKey || !selectedPermissionKeySet.has(action.permissionKey)) {
            return;
          }
          addPermission(
            groupKey,
            node.pageKey,
            pageName,
            node.routePath,
            action.permissionKey,
            resolvePermissionName(action.permissionKey, action.permissionName, permissionCatalogMap),
            false,
          );
        });
      }

      if (node.children?.length) {
        visit(node.children);
      }
    });
  };

  visit(nodes);

  selectedPermissionKeys.forEach((permissionKey) => {
    if (seenPermissionKeys.has(permissionKey)) {
      return;
    }

    const catalog = permissionCatalogMap.get(permissionKey);
    const groupKey = resolvePermissionGroup(catalog?.permissionGroup, permissionKey, permissionCatalogMap);
    const permissionName = resolvePermissionName(permissionKey, catalog?.permissionName || permissionKey, permissionCatalogMap);
    addPermission(groupKey, permissionKey, permissionName, undefined, permissionKey, permissionName, true);
  });

  return Array.from(groupMap.entries())
    .map(([permissionGroup, pageMap]) => ({
      permissionGroup,
      pages: Array.from(pageMap.values()).sort((left, right) => left.pageName.localeCompare(right.pageName, 'zh-Hans-CN')),
    }))
    .sort((left, right) => left.permissionGroup.localeCompare(right.permissionGroup, 'zh-Hans-CN'));
};

interface UseRolePermissionEditorOptions {
  form: FormInstance;
  editorOpen: boolean;
  onDirty: () => void;
}

const useRolePermissionEditor = ({ form, editorOpen, onDirty }: UseRolePermissionEditorOptions) => {
  const [permissionTree, setPermissionTree] = useState<PermissionTreeRecord[]>([]);
  const [permissionCatalog, setPermissionCatalog] = useState<PermissionRecord[]>([]);
  const [permissionTreeLoading, setPermissionTreeLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setPermissionTreeLoading(true);
    void request<PermissionTreeRecord[]>('/v1/system/permissions/tree', {
      method: 'GET',
      ...API_OPTS.NO_REDIRECT,
    })
      .then((result: PermissionTreeRecord[]) => {
        if (!active) return;
        setPermissionTree(result);
      })
      .catch(() => {
        if (active) {
          message.error(t('加载权限树失败，请稍后重试', 'Failed to load the permission tree. Please try again later.'));
        }
      })
      .finally(() => {
        if (active) {
          setPermissionTreeLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;

    void request<PermissionRecord[]>('/v1/system/permissions', {
      method: 'GET',
      ...API_OPTS.NO_REDIRECT,
    })
      .then((result: PermissionRecord[]) => {
        if (active) {
          setPermissionCatalog(result);
        }
      })
      .catch(() => {
        if (active) {
          message.error(t('加载权限信息失败，请稍后重试', 'Failed to load the permission information. Please try again later.'));
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const permissionCatalogMap = useMemo(
    () => new Map(permissionCatalog.map((item) => [item.permissionKey, item] as const)),
    [permissionCatalog],
  );
  const normalizedPermissionTree = useMemo(() => normalizePermissionTree(permissionTree), [permissionTree]);
  const selectablePages = useMemo(() => collectSelectablePages(normalizedPermissionTree), [normalizedPermissionTree]);
  const selectablePageNodeMap = useMemo(() => collectSelectablePageNodeMap(normalizedPermissionTree), [normalizedPermissionTree]);
  const permissionKeyToPageKeyMap = useMemo(() => collectPermissionKeyToPageKeyMap(normalizedPermissionTree), [normalizedPermissionTree]);
  const actionPermissionPageMap = useMemo(() => collectActionPermissionPageMap(normalizedPermissionTree), [normalizedPermissionTree]);
  const assignablePermissionKeys = useMemo(() => collectAssignablePermissionKeys(normalizedPermissionTree), [normalizedPermissionTree]);
  const selectablePermissionKeys = useMemo(
    () => new Set(selectablePages.map((item) => item.permissionKey).filter(Boolean) as string[]),
    [selectablePages],
  );
  const pageTreeData = useMemo(() => buildPermissionTreeData(normalizedPermissionTree), [normalizedPermissionTree]);

  const [editorLoading, setEditorLoading] = useState(false);
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);
  const [activePageKey, setActivePageKey] = useState<string | null>(null);
  const watchedPermissionKeysValue = Form.useWatch<string[]>('permissionKeys', form);
  const watchedPermissionKeys = useMemo(() => watchedPermissionKeysValue ?? [], [watchedPermissionKeysValue]);
  const selectedPagePermissionKeys = useMemo(
    () => watchedPermissionKeys.filter((permissionKey) => selectablePermissionKeys.has(permissionKey)),
    [selectablePermissionKeys, watchedPermissionKeys],
  );
  const selectedPageNodeKeys = useMemo(
    () =>
      Array.from(new Set(selectedPagePermissionKeys.flatMap((permissionKey) => permissionKeyToPageKeyMap.get(permissionKey) || []))),
    [permissionKeyToPageKeyMap, selectedPagePermissionKeys],
  );
  useEffect(() => {
    if (!editorOpen || activePageKey) {
      return;
    }

    setActivePageKey(selectedPageNodeKeys[0] || selectablePages[0]?.pageKey || null);
  }, [activePageKey, editorOpen, selectablePages, selectedPageNodeKeys]);
  const activePageNode = useMemo(
    () => (activePageKey ? selectablePageNodeMap.get(activePageKey) || null : null),
    [activePageKey, selectablePageNodeMap],
  );
  const activePageActionPermissions = useMemo(() => activePageNode?.actionPermissions ?? [], [activePageNode?.actionPermissions]);
  const activePageActionPermissionKeys = useMemo(
    () => new Set(activePageActionPermissions.map((item) => item.permissionKey).filter(Boolean) as string[]),
    [activePageActionPermissions],
  );
  const activePageSelectedActionKeys = useMemo(
    () => watchedPermissionKeys.filter((permissionKey) => activePageActionPermissionKeys.has(permissionKey)),
    [activePageActionPermissionKeys, watchedPermissionKeys],
  );
  const isActivePageSelected = Boolean(activePageNode?.permissionKey && selectedPagePermissionKeys.includes(activePageNode.permissionKey));
  const selectedPageCount = selectedPageNodeKeys.length;
  const sanitizePermissionKeys = useCallback(
    (permissionKeys: string[] = []) => permissionKeys.filter((permissionKey) => assignablePermissionKeys.has(permissionKey)),
    [assignablePermissionKeys],
  );
  const applyPermissionKeys = useCallback(
    (nextPermissionKeys: string[]) => {
      form.setFieldsValue({ permissionKeys: sanitizePermissionKeys(nextPermissionKeys) });
      onDirty();
    },
    [form, onDirty, sanitizePermissionKeys],
  );
  const syncActivePageByPermissionKeys = useCallback(
    (permissionKeys?: string[]) => {
      const sanitizedPermissionKeys = sanitizePermissionKeys(permissionKeys);
      if (permissionKeys && sanitizedPermissionKeys.length !== permissionKeys.length) {
        form.setFieldsValue({ permissionKeys: sanitizedPermissionKeys });
      }
      const initialPermissionKey = sanitizedPermissionKeys.find((permissionKey) => selectablePermissionKeys.has(permissionKey)) || null;
      setActivePageKey(initialPermissionKey ? permissionKeyToPageKeyMap.get(initialPermissionKey)?.[0] || null : null);
    },
    [form, permissionKeyToPageKeyMap, sanitizePermissionKeys, selectablePermissionKeys, setActivePageKey],
  );
  const handlePageTreeCheck = useCallback(
    (checkedKeys: string[]) => {
      const nextPageNodeKeys = checkedKeys.filter((pageKey) => selectablePageNodeMap.has(pageKey));
      const nextPagePermissionKeys = nextPageNodeKeys
        .map((pageKey) => selectablePageNodeMap.get(pageKey)?.permissionKey)
        .filter((permissionKey): permissionKey is string => Boolean(permissionKey));
      const nextPermissionKeys = normalizePermissionKeysByPages(
        watchedPermissionKeys,
        nextPagePermissionKeys,
        selectablePermissionKeys,
        actionPermissionPageMap,
        assignablePermissionKeys,
      );
      applyPermissionKeys(nextPermissionKeys);

      if (!nextPageNodeKeys.length) {
        setActivePageKey(null);
        return;
      }

      if (!activePageKey || !nextPageNodeKeys.includes(activePageKey)) {
        setActivePageKey(nextPageNodeKeys[0]);
      }
    },
    [
      actionPermissionPageMap,
      activePageKey,
      applyPermissionKeys,
      assignablePermissionKeys,
      selectablePageNodeMap,
      selectablePermissionKeys,
      setActivePageKey,
      watchedPermissionKeys,
    ],
  );
  const resetEditorPermissionState = useCallback(() => {
    setActivePageKey(null);
  }, []);
  const handleSelectAllPages = useCallback(() => {
    const allPagePermissionKeys = selectablePages.map((item) => item.permissionKey).filter(Boolean) as string[];
    const nextPagePermissionKeys = selectedPagePermissionKeys.length === allPagePermissionKeys.length ? [] : allPagePermissionKeys;
    const nextPermissionKeys = normalizePermissionKeysByPages(
      watchedPermissionKeys,
      nextPagePermissionKeys,
      selectablePermissionKeys,
      actionPermissionPageMap,
      assignablePermissionKeys,
    );
    applyPermissionKeys(nextPermissionKeys);
    setActivePageKey(nextPagePermissionKeys[0] ? permissionKeyToPageKeyMap.get(nextPagePermissionKeys[0])?.[0] || null : null);
  }, [actionPermissionPageMap, applyPermissionKeys, assignablePermissionKeys, permissionKeyToPageKeyMap, selectablePages, selectablePermissionKeys, selectedPagePermissionKeys.length, watchedPermissionKeys]);
  const handleExpandToggle = useCallback(() => {
    if (!selectablePages.length) {
      setExpandedKeys([]);
      return;
    }

    if (!activePageKey) {
      setExpandedKeys(collectExpandableKeys(normalizedPermissionTree));
      return;
    }

    setExpandedKeys([]);
  }, [activePageKey, normalizedPermissionTree, selectablePages.length, setExpandedKeys]);
  const handleActionPermissionsChange = useCallback(
    (nextActionKeys: string[]) => {
      if (!activePageNode?.permissionKey) {
        return;
      }

      const nextPermissionKeys = new Set<string>(watchedPermissionKeys.filter((permissionKey) => assignablePermissionKeys.has(permissionKey)));
      activePageActionPermissions.forEach((action) => {
        if (action.permissionKey) {
          nextPermissionKeys.delete(action.permissionKey);
        }
      });

      nextPermissionKeys.add(activePageNode.permissionKey);
      nextActionKeys.forEach((permissionKey) => {
        nextPermissionKeys.add(permissionKey);
      });

      form.setFieldsValue({ permissionKeys: Array.from(nextPermissionKeys) });
      onDirty();
    },
    [activePageActionPermissions, activePageNode?.permissionKey, assignablePermissionKeys, form, onDirty, watchedPermissionKeys],
  );

  return {
    permissionTree,
    permissionCatalogMap,
    permissionTreeLoading,
    editorLoading,
    setEditorLoading,
    pageTreeData,
    selectedPageNodeKeys,
    selectedPageCount,
    totalPageCount: selectablePages.length,
    activePageKey,
    setActivePageKey,
    activePageNode,
    activePageActionPermissions,
    activePageSelectedActionKeys,
    isActivePageSelected,
    expandedKeys,
    setExpandedKeys,
    resetEditorPermissionState,
    syncActivePageByPermissionKeys,
    handlePageTreeCheck,
    handleSelectAllPages,
    handleExpandToggle,
    handleActionPermissionsChange,
    sanitizePermissionKeys,
  };
};

export const useRoleManagementPageData = () => {
  const roleCrud = useCrudPageState<RoleRecord>();
  const { actionPermission, responsive, searchConfig, buildToolbarButtons } = usePagePermissionActions();
  const canUpdateRoleSettings = actionPermission.can('system:role:update');
  const [editorForm] = Form.useForm<RoleEditorFormValues>();
  const [selectedRoleDetail, setSelectedRoleDetail] = useState<RoleDetail | null>(null);
  const [editorDirty, setEditorDirty] = useState(false);
  const [roleEditorMode, setRoleEditorMode] = useState<RoleEditorMode>('create');
  const [saving, setSaving] = useState(false);
  const [defaultRoleModalOpen, setDefaultRoleModalOpen] = useState(false);
  const [defaultRoleOptions, setDefaultRoleOptions] = useState<RoleRecord[]>([]);
  const [defaultRoleId, setDefaultRoleId] = useState<number | undefined>();
  const [defaultRoleLoading, setDefaultRoleLoading] = useState(false);
  const [defaultRoleSaving, setDefaultRoleSaving] = useState(false);
  const permissionEditor = useRolePermissionEditor({
    form: editorForm,
    editorOpen: roleCrud.drawer.open,
    onDirty: () => undefined,
  });
  const detailProps = useDetailProDescriptionsProps<RoleDetail>({
    column: responsive.isMobile ? 1 : 2,
    dataSource: selectedRoleDetail || undefined,
  });
  const openDefaultRoleModal = useCallback(async () => {
    setDefaultRoleModalOpen(true);
    setDefaultRoleLoading(true);
    try {
      const [defaultRole, rolePage] = await Promise.all([
        request<RoleDetail>('/v1/system/roles/default-registration-role', {
          method: 'GET',
          ...API_OPTS.NO_REDIRECT,
        }),
        request<PagedResult<RoleRecord>>('/v1/system/roles', {
          method: 'GET',
          params: { pageNo: 1, pageSize: 200 },
          ...API_OPTS.NO_REDIRECT,
        }),
      ]);
      setDefaultRoleId(defaultRole.id);
      setDefaultRoleOptions(rolePage.records || []);
    } catch {
      message.error(t('默认注册角色加载失败，请稍后重试', 'Failed to load the default registration role. Please try again later.'));
    } finally {
      setDefaultRoleLoading(false);
    }
  }, []);
  const saveDefaultRole = useCallback(async () => {
    if (!defaultRoleId) {
      message.warning(t('请选择默认注册角色', 'Please choose a default registration role'));
      return;
    }
    setDefaultRoleSaving(true);
    try {
      await request<RoleDetail>('/v1/system/roles/default-registration-role', {
        method: 'PUT',
        data: { roleId: defaultRoleId },
        ...API_OPTS.NO_REDIRECT,
      });
      message.success(t('默认注册角色已更新', 'Default registration role updated'));
      setDefaultRoleModalOpen(false);
      roleCrud.reloadTable();
    } finally {
      setDefaultRoleSaving(false);
    }
  }, [defaultRoleId, roleCrud]);
  const defaultRoleModal = {
    open: defaultRoleModalOpen,
    loading: defaultRoleLoading,
    saving: defaultRoleSaving,
    canSave: canUpdateRoleSettings,
    value: defaultRoleId,
    options: defaultRoleOptions,
    onChange: setDefaultRoleId,
    onSubmit: () => void saveDefaultRole(),
    onCancel: () => setDefaultRoleModalOpen(false),
  };
  const handleRoleCodeBlur = useCallback(() => {
    const currentRoleCode = editorForm.getFieldValue('roleCode');
    if (typeof currentRoleCode !== 'string') {
      return;
    }
    const trimmedRoleCode = currentRoleCode.trim();
    if (trimmedRoleCode !== currentRoleCode) {
      editorForm.setFieldsValue({ roleCode: trimmedRoleCode });
    }
  }, [editorForm]);
  const closeEditorDrawer = useCallback(() => {
    roleCrud.drawer.close();
    setRoleEditorMode('create');
    permissionEditor.setEditorLoading(false);
    setEditorDirty(false);
    permissionEditor.resetEditorPermissionState();
  }, [permissionEditor, roleCrud.drawer]);
  const openEdit = useCallback(
    async (record: RoleRecord, mode: RoleEditorMode = 'edit') => {
      roleCrud.drawer.openEdit(record, record.id);
      setRoleEditorMode(mode);
      permissionEditor.resetEditorPermissionState();
      permissionEditor.setEditorLoading(true);
      setEditorDirty(false);

      try {
        const detail = await request<RoleDetail>(`/v1/system/roles/${record.id}`, {
          method: 'GET',
          ...API_OPTS.NO_REDIRECT,
        });
        const permissionKeys = permissionEditor.sanitizePermissionKeys(detail.permissionKeys || []);
        editorForm.setFieldsValue({
          ...detail,
          defaultHomePath: detail.defaultHomePath || DEFAULT_HOME_PATH,
          permissionKeys,
          dataScopes: detail.dataScopes?.length ? detail.dataScopes : DEFAULT_DATA_SCOPES,
        });
        permissionEditor.syncActivePageByPermissionKeys(permissionKeys);
      } catch {
      message.error(t('加载角色信息失败，请稍后重试', 'Failed to load the role information. Please try again later.'));
        roleCrud.drawer.close();
      } finally {
        permissionEditor.setEditorLoading(false);
      }
    },
    [editorForm, permissionEditor, roleCrud.drawer],
  );
  const closeEditorIfClean = useCallback(() => {
    if (!editorDirty) {
      closeEditorDrawer();
      return;
    }

    confirmAction({
      title: t('提示', 'Notice'),
      content: t('关闭抽屉将丢失未保存的内容，是否确认关闭？', 'Closing the drawer will discard unsaved changes. Do you want to continue?'),
      okText: t('继续编辑', 'Keep editing'),
      cancelText: t('确认关闭', 'Close anyway'),
      centered: true,
      onOk: () => Promise.resolve(),
      onCancel: closeEditorDrawer,
    });
  }, [closeEditorDrawer, editorDirty]);
  const canSaveRole =
    roleEditorMode === 'permissions'
      ? actionPermission.can('system:role:permissions')
      : actionPermission.can(roleCrud.drawer.editingId ? 'system:role:update' : 'system:role:create');
  const isPermissionOnlyEditor = roleEditorMode === 'permissions';
  const editorFormProps = useStandardFormProps({
    form: editorForm,
    initialValues: { roleType: 'CUSTOM', defaultHomePath: DEFAULT_HOME_PATH, permissionKeys: [], dataScopes: DEFAULT_DATA_SCOPES },
    onValuesChange: () => setEditorDirty(true),
    className: 'role-editor-form',
  });
  const buildRoleEditorPayload = useCallback(
    (values: RoleEditorFormValues) => ({
      ...values,
      roleCode: String(values.roleCode || '').trim(),
      roleName: String(values.roleName || '').trim(),
      roleType: String(values.roleType || 'CUSTOM'),
      defaultHomePath: typeof values.defaultHomePath === 'string' ? values.defaultHomePath.trim() : DEFAULT_HOME_PATH,
      permissionKeys: values.permissionKeys || [],
      dataScopes: (values.dataScopes?.length ? values.dataScopes : DEFAULT_DATA_SCOPES).map((item) => ({
        resourceCode: String(item.resourceCode || '*'),
        scopeType: (item.scopeType || 'SELF') as import('@/types/api').RoleDataScope['scopeType'],
      })),
    }),
    [],
  );
  const saveRole = useCallback(async () => {
    setSaving(true);
    try {
      const values = await editorForm.validateFields();
      const permissionKeys = permissionEditor.sanitizePermissionKeys(values.permissionKeys || []);
      if (roleCrud.drawer.editingId && roleEditorMode === 'permissions') {
        await request<boolean>(`/v1/system/roles/${roleCrud.drawer.editingId}/permissions`, {
          method: 'PUT',
          data: { permissionKeys },
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('角色权限已更新', 'Role permissions updated'));
        closeEditorDrawer();
        roleCrud.reloadTable();
        return;
      }
      const payload = {
        ...buildRoleEditorPayload(values),
        permissionKeys,
      };
      if (roleCrud.drawer.editingId) {
        await request<RoleDetail>(`/v1/system/roles/${roleCrud.drawer.editingId}`, {
          method: 'PUT',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('角色已更新', 'Role updated'));
      } else {
        await request<RoleDetail>('/v1/system/roles', {
          method: 'POST',
          data: payload,
          ...API_OPTS.NO_REDIRECT,
        });
        message.success(t('角色已创建', 'Role created'));
      }
      closeEditorDrawer();
      roleCrud.reloadTable();
    } finally {
      setSaving(false);
    }
  }, [buildRoleEditorPayload, closeEditorDrawer, editorForm, permissionEditor, roleCrud, roleEditorMode]);
  const deleteRole = useCallback(
    (record: RoleRecord) => {
      confirmAction({
        title: t('删除角色', 'Delete role'),
        content: t(
          `确认删除角色「${record.roleName}」吗？删除后该角色的权限配置会一并移除。`,
          `Delete role "${record.roleName}"? This will also remove its permission settings.`,
        ),
        okText: t('确认删除', 'Delete'),
        okButtonProps: { danger: true },
        onOk: async () => {
          await request<boolean>(`/v1/system/roles/${record.id}`, {
            method: 'DELETE',
            ...API_OPTS.NO_REDIRECT,
          });
          message.success(t('角色已删除', 'Role deleted'));
          roleCrud.reloadTable();
        },
      });
    },
    [roleCrud],
  );
  const openDetail = async (record: RoleRecord) => {
    roleCrud.detail.openDetail(record);
    roleCrud.detail.setLoading(true);
    try {
      const detail = await request<RoleDetail>(`/v1/system/roles/${record.id}`, {
        method: 'GET',
        autoRedirectOnUnauthorized: false,
        silent: true,
      });
      setSelectedRoleDetail(detail);
    } finally {
      roleCrud.detail.setLoading(false);
    }
  };
  const roleActions = {
    selectedRoleDetail,
    roleEditorMode,
    editorDirty,
    saving,
    canSaveRole,
    isPermissionOnlyEditor,
    editorFormProps,
    detailProps,
    handleRoleCodeBlur,
    closeEditorDrawer,
    openEdit,
    openDetail,
    saveRole,
    deleteRole,
    handleEditorClose: closeEditorIfClean,
    setSelectedRoleDetail,
    setEditorDirty,
    setRoleEditorMode,
  };
  const permissionDetailGroups = useMemo(
    () =>
      buildRolePermissionDisplayGroups(
        permissionEditor.pageTreeData,
        selectedRoleDetail?.permissionKeys || [],
        permissionEditor.permissionCatalogMap,
      ),
    [permissionEditor.pageTreeData, permissionEditor.permissionCatalogMap, selectedRoleDetail?.permissionKeys],
  );
  const defaultHomeOptions = useMemo(() => collectDefaultHomeOptions(permissionEditor.pageTreeData), [permissionEditor.pageTreeData]);
  const columns = [
    ...roleDataColumns,
    buildRoleActionColumn({
      isDesktop: responsive.isDesktop,
      isMobile: responsive.isMobile,
      buildRowActions: actionPermission.buildTableActions,
      onOpenDetail: (record) => void openDetail(record),
      onOpenEdit: (record) => void openEdit(record),
      onOpenPermissions: (record) => void openEdit(record, 'permissions'),
      onDelete: roleActions.deleteRole,
    }),
  ];
  const tableRequest = buildTableRequest((params) =>
    request<PagedResult<RoleRecord>>('/v1/system/roles', {
      method: 'GET',
      params,
      ...API_OPTS.NO_REDIRECT,
    }),
  );

  return {
    roleCrud,
    searchConfig,
    buildToolbarButtons,
    responsive,
    columns,
    tableRequest,
    defaultRoleModal,
    openDefaultRoleModal,
    permissionEditor,
    roleActions,
    permissionDetailGroups,
    defaultHomeOptions,
  };
};
