import buildAccess from '@/access';
import { realPageRouteMetaList, resolveCanonicalRoutePath } from '@/routes/meta';
import type { CurrentUser, MenuNode } from '@/types/api';

type BootstrapMenuNode = MenuNode & { access?: string };

const COMPETITION_MENU_ROOT: MenuNode = {
  id: -1070,
  menuCode: 'competition.root',
  name: '\u8d5b\u4e8b',
  path: '/competitions',
  component: 'redirect:/competitions/register',
  icon: 'TrophyOutlined',
  sortNo: 5,
  children: [],
};

const REGISTRATION_MENU_ROOT: MenuNode = {
  id: -1069,
  menuCode: 'registration.root',
  name: '\u62a5\u540d',
  path: '/registration',
  component: 'redirect:/competitions/register',
  icon: 'FormOutlined',
  sortNo: 4,
  children: [],
};

const EXPERT_REVIEW_MENU_ROOT: MenuNode = {
  id: -1068,
  menuCode: 'expert.review.root',
  name: 'nav.expertReview.root',
  path: '/expert-review',
  component: 'redirect:/expert-review/reviews',
  icon: 'SolutionOutlined',
  sortNo: 6,
  children: [],
};

const COMPETITION_APPLICATION_MENUS: BootstrapMenuNode[] = [];

const EXPERT_REVIEW_APPLICATION_MENUS: BootstrapMenuNode[] = [
  {
    id: -1078,
    parentId: -1068,
    menuCode: 'expert.review.tasks',
    name: 'nav.expertReview.reviews',
    path: '/expert-review/reviews',
    component: '@/pages/competition/CompetitionReviewPage',
    icon: 'AuditOutlined',
    sortNo: 1,
    access: 'canVisitReviewWorkbench',
  },
  {
    id: -1077,
    parentId: -1068,
    menuCode: 'expert.application',
    name: '\u4e13\u5bb6\u7533\u8bf7',
    path: '/competitions/expert-apply',
    component: '@/pages/competition',
    icon: 'SolutionOutlined',
    sortNo: 2,
    access: 'canVisitExperts',
  },
];

const REGISTRATION_APPLICATION_MENUS: BootstrapMenuNode[] = [
  {
    id: -1075,
    parentId: -1069,
    menuCode: 'competition.registration',
    name: '\u8d5b\u4e8b\u62a5\u540d',
    path: '/competitions/register',
    component: '@/pages/competition',
    icon: 'FormOutlined',
    sortNo: 1,
    access: 'canVisitCompetitionRegister',
  },
  {
    id: -1076,
    parentId: -1069,
    menuCode: 'activity.registration',
    name: '\u6d3b\u52a8\u62a5\u540d',
    path: '/activities/register',
    component: '@/pages/competition',
    icon: 'CalendarOutlined',
    sortNo: 2,
    access: 'canVisitActivityRegister',
  },
  {
    id: -1074,
    parentId: -1069,
    menuCode: 'competition.review-results',
    name: 'nav.competitions.reviewResults',
    path: '/competitions/review-results',
    component: '@/pages/competition/CompetitionReviewResultsPage',
    icon: 'FileSearchOutlined',
    sortNo: 3,
    access: 'canVisitCompetitionReviewResults',
  },
];

const hasMenuPath = (menus: MenuNode[] | undefined, path: string): boolean =>
  Boolean(menus?.some((menu) => menu.path === path || hasMenuPath(menu.children, path)));

const routePathMatches = (routePath: string, pathname: string) => {
  const pattern = routePath
    .replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    .replace(/:([^/]+)/g, '[^/]+');

  return new RegExp(`^${pattern}$`).test(pathname);
};

const canVisitMenuPath = (path: string | undefined, currentUser: CurrentUser) => {
  if (!path) {
    return true;
  }
  const canonicalPath = resolveCanonicalRoutePath(path);
  const routeMeta = realPageRouteMetaList.find((item) => routePathMatches(item.path, canonicalPath));
  if (!routeMeta?.access) {
    return true;
  }
  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  return Boolean(access[routeMeta.access]);
};

const filterMenusByAccess = (menus: MenuNode[] | undefined, currentUser: CurrentUser): MenuNode[] =>
  (menus || [])
    .map((menu) => {
      const children = filterMenusByAccess(menu.children, currentUser);
      if (!canVisitMenuPath(menu.path, currentUser) && children.length === 0) {
        return null;
      }
      return {
        ...menu,
        ...(menu.children || children.length ? { children } : {}),
      };
    })
    .filter((menu): menu is MenuNode => Boolean(menu));

const mergeCompetitionApplicationChildren = (children: MenuNode[] | undefined, currentUser: CurrentUser) => {
  const nextChildren = [...(children || [])];
  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  COMPETITION_APPLICATION_MENUS.forEach((requiredMenu) => {
    if (requiredMenu.access && !access[requiredMenu.access]) {
      return;
    }
    if (!hasMenuPath(nextChildren, requiredMenu.path)) {
      nextChildren.push({ ...requiredMenu });
    }
  });
  nextChildren.sort((left, right) => (left.sortNo ?? 0) - (right.sortNo ?? 0));
  return nextChildren;
};

const ensureCompetitionApplicationMenus = (menus: MenuNode[], currentUser: CurrentUser): MenuNode[] => {
  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  const visibleCompetitionMenus = COMPETITION_APPLICATION_MENUS.filter((menu) => !menu.access || Boolean(access[menu.access]));
  const normalizedMenus = menus;
  if (!visibleCompetitionMenus.length) {
    return normalizedMenus;
  }
  if (visibleCompetitionMenus.every((menu) => hasMenuPath(normalizedMenus, menu.path))) {
    return normalizedMenus;
  }

  let attached = false;
  const nextMenus = normalizedMenus.map((menu) => {
    const isCompetitionRoot = menu.menuCode === COMPETITION_MENU_ROOT.menuCode || menu.path === COMPETITION_MENU_ROOT.path;
    if (!isCompetitionRoot) {
      return menu;
    }

    attached = true;
    return {
      ...menu,
      children: mergeCompetitionApplicationChildren(menu.children, currentUser),
    };
  });

  return attached
    ? nextMenus
    : [
        ...nextMenus,
        {
          ...COMPETITION_MENU_ROOT,
          children: mergeCompetitionApplicationChildren(COMPETITION_MENU_ROOT.children, currentUser),
        },
      ];
};

const isExpertReviewApplicationMenu = (menu: MenuNode) =>
  menu.menuCode === 'expert.application'
  || menu.menuCode === 'expert.review.application'
  || menu.path === '/competitions/expert-apply';

const removeExpertReviewApplicationMenus = (menus: MenuNode[]): MenuNode[] =>
  menus
    .filter((menu) => !isExpertReviewApplicationMenu(menu))
    .map((menu) => ({
      ...menu,
      ...(menu.children ? { children: removeExpertReviewApplicationMenus(menu.children) } : {}),
    }));

const mergeExpertReviewApplicationChildren = (children: MenuNode[] | undefined, currentUser: CurrentUser) => {
  const nextChildren = (children || []).filter(
    (menu) => !isExpertReviewApplicationMenu(menu),
  );
  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  EXPERT_REVIEW_APPLICATION_MENUS.forEach((requiredMenu) => {
    if (requiredMenu.access && !access[requiredMenu.access]) {
      return;
    }
    if (!hasMenuPath(nextChildren, requiredMenu.path)) {
      nextChildren.push({ ...requiredMenu });
    }
  });
  nextChildren.sort((left, right) => (left.sortNo ?? 0) - (right.sortNo ?? 0));
  return nextChildren;
};

const ensureExpertReviewApplicationMenus = (menus: MenuNode[], currentUser: CurrentUser): MenuNode[] => {
  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  const visibleExpertReviewMenus = EXPERT_REVIEW_APPLICATION_MENUS.filter((menu) => !menu.access || Boolean(access[menu.access]));
  const normalizedMenus = removeExpertReviewApplicationMenus(menus);
  if (!visibleExpertReviewMenus.length) {
    return normalizedMenus;
  }

  let attached = false;
  const nextMenus = normalizedMenus.map((menu) => {
    const isExpertReviewRoot = menu.menuCode === EXPERT_REVIEW_MENU_ROOT.menuCode || menu.path === EXPERT_REVIEW_MENU_ROOT.path;
    if (!isExpertReviewRoot) {
      return menu;
    }

    attached = true;
    return {
      ...menu,
      path: EXPERT_REVIEW_MENU_ROOT.path,
      component: EXPERT_REVIEW_MENU_ROOT.component,
      children: mergeExpertReviewApplicationChildren(menu.children, currentUser),
    };
  });

  return attached
    ? nextMenus
    : [
        ...nextMenus,
        {
          ...EXPERT_REVIEW_MENU_ROOT,
          children: mergeExpertReviewApplicationChildren(EXPERT_REVIEW_MENU_ROOT.children, currentUser),
        },
      ];
};

const isRegistrationApplicationMenu = (menu: MenuNode) =>
  menu.menuCode === 'competition.registration'
  || menu.menuCode === 'activity.registration'
  || menu.path === '/competitions/register'
  || menu.path === '/competitions/activity-register'
  || menu.path === '/activities/register';

const removeRegistrationApplicationMenus = (menus: MenuNode[]): MenuNode[] =>
  menus
    .filter((menu) => !isRegistrationApplicationMenu(menu))
    .map((menu) => ({
      ...menu,
      ...(menu.children ? { children: removeRegistrationApplicationMenus(menu.children) } : {}),
    }));

const mergeRegistrationApplicationChildren = (children: MenuNode[] | undefined, currentUser: CurrentUser) => {
  const nextChildren = (children || []).filter(
    (menu) => !isRegistrationApplicationMenu(menu),
  );
  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  REGISTRATION_APPLICATION_MENUS.forEach((requiredMenu) => {
    if (requiredMenu.access && !access[requiredMenu.access]) {
      return;
    }
    if (!hasMenuPath(nextChildren, requiredMenu.path)) {
      nextChildren.push({ ...requiredMenu });
    }
  });
  nextChildren.sort((left, right) => (left.sortNo ?? 0) - (right.sortNo ?? 0));
  return nextChildren;
};

const ensureRegistrationApplicationMenus = (menus: MenuNode[], currentUser: CurrentUser): MenuNode[] => {
  const access = buildAccess({ currentUser }) as Record<string, unknown>;
  const visibleRegistrationMenus = REGISTRATION_APPLICATION_MENUS.filter((menu) => !menu.access || Boolean(access[menu.access]));
  const normalizedMenus = removeRegistrationApplicationMenus(menus);
  if (!visibleRegistrationMenus.length) {
    return normalizedMenus;
  }

  let attached = false;
  const nextMenus = normalizedMenus.map((menu) => {
    const isRegistrationRoot = menu.menuCode === REGISTRATION_MENU_ROOT.menuCode || menu.path === REGISTRATION_MENU_ROOT.path;
    if (!isRegistrationRoot) {
      return menu;
    }

    attached = true;
    return {
      ...menu,
      path: REGISTRATION_MENU_ROOT.path,
      component: REGISTRATION_MENU_ROOT.component,
      children: mergeRegistrationApplicationChildren(menu.children, currentUser),
    };
  });

  return attached
    ? nextMenus
    : [
        ...nextMenus,
        {
          ...REGISTRATION_MENU_ROOT,
          children: mergeRegistrationApplicationChildren(REGISTRATION_MENU_ROOT.children, currentUser),
        },
      ];
};

export const normalizeAuthenticatedMenuTree = (
  menuTree: MenuNode[] | undefined,
  currentUser: CurrentUser,
): MenuNode[] =>
  ensureRegistrationApplicationMenus(
    ensureExpertReviewApplicationMenus(
      ensureCompetitionApplicationMenus(filterMenusByAccess(menuTree, currentUser), currentUser),
      currentUser,
    ),
    currentUser,
  );
