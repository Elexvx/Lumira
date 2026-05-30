import { Form, message } from 'antd';
import type { FormInstance } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { iamService } from '@/services/iam';
import type { PermissionRecord, PermissionTreeRecord } from '@/types/api';
import { API_OPTS, showErrorMessage } from '@/utils/errorMessage';

import {
  buildPermissionTreeData,
  collectActionPermissionPageMap,
  collectAssignablePermissionKeys,
  collectExpandableKeys,
  collectPermissionKeyToPageKeyMap,
  collectSelectablePageNodeMap,
  collectSelectablePages,
  normalizePermissionKeysByPages,
  normalizePermissionTree,
} from '@/pages/system/rolesPermissionTree';

interface UseRolePermissionEditorOptions {
  form: FormInstance;
  editorOpen: boolean;
  onDirty: () => void;
}

export const useRolePermissionEditor = ({ form, editorOpen, onDirty }: UseRolePermissionEditorOptions) => {
  const [permissionTree, setPermissionTree] = useState<PermissionTreeRecord[]>([]);
  const [permissionCatalog, setPermissionCatalog] = useState<PermissionRecord[]>([]);
  const [permissionTreeLoading, setPermissionTreeLoading] = useState(true);
  const [editorLoading, setEditorLoading] = useState(false);
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);
  const [activePageKey, setActivePageKey] = useState<string | null>(null);
  const watchedPermissionKeys = Form.useWatch<string[]>('permissionKeys', form) ?? [];

  useEffect(() => {
    let active = true;
    setPermissionTreeLoading(true);
    void iamService
      .permissionTree(API_OPTS.NO_REDIRECT)
      .then((result: PermissionTreeRecord[]) => {
        if (!active) {
          return;
        }
        setPermissionTree(result);
        setExpandedKeys([]);
      })
      .catch(() => {
        if (active) {
          message.error('加载权限树失败，请稍后重试');
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

    void iamService
      .permissions(API_OPTS.NO_REDIRECT)
      .then((result: PermissionRecord[]) => {
        if (active) {
          setPermissionCatalog(result);
        }
      })
      .catch(() => {
        if (active) {
          message.error('加载权限信息失败，请稍后重试');
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const normalizedPermissionTree = useMemo(() => normalizePermissionTree(permissionTree), [permissionTree]);
  const permissionCatalogMap = useMemo(
    () => new Map(permissionCatalog.map((item) => [item.permissionKey, item] as const)),
    [permissionCatalog],
  );
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

  const selectedPagePermissionKeys = useMemo(
    () => watchedPermissionKeys.filter((permissionKey) => selectablePermissionKeys.has(permissionKey)),
    [selectablePermissionKeys, watchedPermissionKeys],
  );

  const selectedPageNodeKeys = useMemo(
    () =>
      Array.from(new Set(selectedPagePermissionKeys.flatMap((permissionKey) => permissionKeyToPageKeyMap.get(permissionKey) || []))),
    [permissionKeyToPageKeyMap, selectedPagePermissionKeys],
  );

  const activePageNode = useMemo(
    () => (activePageKey ? selectablePageNodeMap.get(activePageKey) || null : null),
    [activePageKey, selectablePageNodeMap],
  );

  const activePageActionPermissions = activePageNode?.actionPermissions ?? [];
  const activePageActionPermissionKeys = useMemo(
    () => new Set(activePageActionPermissions.map((item) => item.permissionKey).filter(Boolean) as string[]),
    [activePageActionPermissions],
  );
  const activePageSelectedActionKeys = useMemo(
    () => watchedPermissionKeys.filter((permissionKey) => activePageActionPermissionKeys.has(permissionKey)),
    [activePageActionPermissionKeys, watchedPermissionKeys],
  );
  const isActivePageSelected = Boolean(activePageNode?.permissionKey && selectedPagePermissionKeys.includes(activePageNode.permissionKey));

  useEffect(() => {
    if (!editorOpen || activePageKey) {
      return;
    }

    setActivePageKey(selectedPageNodeKeys[0] || selectablePages[0]?.pageKey || null);
  }, [activePageKey, editorOpen, selectablePages, selectedPageNodeKeys]);

  const sanitizePermissionKeys = (permissionKeys: string[] = []) => permissionKeys.filter((permissionKey) => assignablePermissionKeys.has(permissionKey));

  const applyPermissionKeys = (nextPermissionKeys: string[]) => {
    form.setFieldsValue({ permissionKeys: sanitizePermissionKeys(nextPermissionKeys) });
    onDirty();
  };

  const resetEditorPermissionState = () => {
    setActivePageKey(null);
    setExpandedKeys([]);
  };

  const syncActivePageByPermissionKeys = (permissionKeys?: string[]) => {
    const sanitizedPermissionKeys = sanitizePermissionKeys(permissionKeys);
    if (permissionKeys && sanitizedPermissionKeys.length !== permissionKeys.length) {
      form.setFieldsValue({ permissionKeys: sanitizedPermissionKeys });
    }
    const initialPermissionKey = sanitizedPermissionKeys.find((permissionKey) => selectablePermissionKeys.has(permissionKey)) || null;
    setActivePageKey(initialPermissionKey ? permissionKeyToPageKeyMap.get(initialPermissionKey)?.[0] || null : null);
  };

  const handlePageTreeCheck = (checkedKeys: string[]) => {
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
  };

  const handleSelectAllPages = () => {
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
  };

  const handleExpandToggle = () => {
    if (!expandedKeys.length) {
      setExpandedKeys(collectExpandableKeys(normalizedPermissionTree));
      return;
    }
    setExpandedKeys([]);
  };

  const handleActionPermissionsChange = (nextActionKeys: string[]) => {
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

    applyPermissionKeys(Array.from(nextPermissionKeys));
  };

  return {
    permissionTree,
    permissionCatalogMap,
    permissionTreeLoading,
    editorLoading,
    setEditorLoading,
    pageTreeData,
    selectedPageNodeKeys,
    selectedPageCount: selectedPageNodeKeys.length,
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
