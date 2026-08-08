import { DEFAULT_HOME_PATH } from '@/app.constants';

type RouteAccess = Record<string, unknown>;
type LandingCandidate = readonly [accessKey: string, path: string];

const resolveFirstAccessiblePath = (
  access: unknown,
  candidates: readonly LandingCandidate[],
) => candidates.find(([accessKey]) => Boolean((access as RouteAccess)?.[accessKey]))?.[1] || DEFAULT_HOME_PATH;

export const resolveDataManagementLandingPath = (access: unknown) => resolveFirstAccessiblePath(access, [
  ['canVisitCompetitions', '/competitions/management'],
  ['canVisitCompetitionRegistrations', '/competitions/registrations'],
  ['canVisitActivities', '/activities/management'],
  ['canVisitPaymentOrders', '/payments/management'],
  ['canVisitDownloadCenter', '/data-management/download-center'],
]);

export const resolveExpertReviewLandingPath = (access: unknown) => resolveFirstAccessiblePath(access, [
  ['canVisitReviewWorkbench', '/expert-review/reviews'],
  ['canVisitExperts', '/experts/management'],
]);

export const resolveWorkflowLandingPath = (access: unknown) => resolveFirstAccessiblePath(access, [
  ['canVisitWorkflowTasks', '/workflows/tasks'],
  ['canVisitWorkflowConfig', '/workflows/config'],
]);

export const resolveUserCenterLandingPath = (access: unknown) => resolveFirstAccessiblePath(access, [
  ['canVisitSystemUsers', '/user-center/users'],
  ['canVisitSystemDepartments', '/user-center/departments'],
  ['canVisitSystemOnlineUsers', '/user-center/online-users'],
  ['canVisitSystemRoles', '/user-center/roles'],
]);

export const resolvePersonalCenterLandingPath = (access: unknown) => resolveFirstAccessiblePath(access, [
  ['canVisitProfile', '/user-center/personal-center/profile'],
  ['canVisitSystemMyFiles', '/user-center/personal-center/files'],
]);
