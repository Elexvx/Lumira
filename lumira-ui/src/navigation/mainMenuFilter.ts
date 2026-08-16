import type { MenuNode } from '@/types/api';

const RETIRED_MAIN_MENU_PATHS = new Set([
  '/projects/management',
  '/team/management',
  '/data-management/query-center',
  '/team/search',
  '/projects/search',
  '/activities/search',
  '/payments/status',
  '/experts/query',
  '/certificates/generate',
]);
const CERTIFICATE_ROOT_MENU_CODE = 'certificate.root';
const CERTIFICATE_MANAGEMENT_MENU_CODES = new Set([
  'certificate.templates',
  'certificate.records',
]);
const normalizeMenuPath = (path: string) => path.trim().replace(/\/+$/, '') || '/';

export const isRetiredMainMenuPath = (path?: string | null) =>
  Boolean(path && RETIRED_MAIN_MENU_PATHS.has(normalizeMenuPath(path)));

export const filterRetiredMainMenuNodes = (
  items: MenuNode[] | undefined,
  legacyRootMenuCode?: string,
): MenuNode[] => {
  const filteredItems = (items || []).flatMap((item) => {
    const children = filterRetiredMainMenuNodes(item.children, legacyRootMenuCode);
    if (isRetiredMainMenuPath(item.path)) {
      return [];
    }
    if (legacyRootMenuCode && item.menuCode === legacyRootMenuCode) {
      return children;
    }
    return {
      ...item,
      children: children.length ? children : undefined,
    };
  });

  const legacyCertificateRoot = filteredItems.find((item) => item.menuCode === CERTIFICATE_ROOT_MENU_CODE);
  const dataManagementRootIndex = filteredItems.findIndex((item) => item.menuCode === 'data.management.root');
  if (!legacyCertificateRoot || dataManagementRootIndex < 0) {
    return filteredItems;
  }

  const certificateManagementChildren = (legacyCertificateRoot.children || [])
    .filter((child) => CERTIFICATE_MANAGEMENT_MENU_CODES.has(child.menuCode));
  const consolidatedItems = filteredItems.filter((item) => item.menuCode !== CERTIFICATE_ROOT_MENU_CODE);
  const consolidatedDataManagementIndex = consolidatedItems.findIndex((item) => item.menuCode === 'data.management.root');
  if (consolidatedDataManagementIndex < 0) {
    return consolidatedItems;
  }

  const dataManagementRoot = consolidatedItems[consolidatedDataManagementIndex];
  consolidatedItems[consolidatedDataManagementIndex] = {
    ...dataManagementRoot,
    children: [
      ...(dataManagementRoot.children || []),
      ...certificateManagementChildren.map((child) => ({
        ...child,
        parentId: dataManagementRoot.id,
      })),
    ],
  };
  return consolidatedItems;
};
