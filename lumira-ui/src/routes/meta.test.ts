import { describe, expect, it } from 'vitest';
import { backendRouteMeta, backendRoutes, isCanonicalRealPageRoutePath, realPageRouteMetaMap, resolveCanonicalRoutePath, systemRouteMeta, systemRoutes } from './meta';

describe('route meta', () => {
  it('keeps personal file center under the personal center route group', () => {
    expect(resolveCanonicalRoutePath('/user-center/files')).toBe('/user-center/personal-center/files');
    expect(resolveCanonicalRoutePath('/user-center/files/')).toBe('/user-center/personal-center/files');
    expect(realPageRouteMetaMap.has('/user-center/personal-center/files')).toBe(true);
    expect(realPageRouteMetaMap.has('/user-center/files')).toBe(false);
  });

  it('removes standalone management and query-center pages', () => {
    expect(resolveCanonicalRoutePath('/projects')).toBe('/dashboard/home');
    expect(resolveCanonicalRoutePath('/projects/management')).toBe('/dashboard/home');
    expect(resolveCanonicalRoutePath('/projects/search')).toBe('/dashboard/home');
    expect(resolveCanonicalRoutePath('/team')).toBe('/dashboard/home');
    expect(resolveCanonicalRoutePath('/team/management')).toBe('/dashboard/home');
    expect(resolveCanonicalRoutePath('/team/search')).toBe('/dashboard/home');
    expect(resolveCanonicalRoutePath('/activities/search')).toBe('/activities/management');
    expect(resolveCanonicalRoutePath('/payments/status')).toBe('/payments/management');
    expect(resolveCanonicalRoutePath('/data-management/query-center')).toBe('/dashboard/home');
    expect(realPageRouteMetaMap.has('/data-management/query-center')).toBe(false);
    expect(isCanonicalRealPageRoutePath('/team')).toBe(false);
    expect(isCanonicalRealPageRoutePath('/data-management/query-center')).toBe(false);
    expect(isCanonicalRealPageRoutePath('/dashboard/home')).toBe(true);
    expect(realPageRouteMetaMap.has('/projects/management')).toBe(false);
    expect(realPageRouteMetaMap.has('/team/management')).toBe(false);
    expect(realPageRouteMetaMap.has('/projects/search')).toBe(false);
    expect(realPageRouteMetaMap.has('/team/search')).toBe(false);
    expect(realPageRouteMetaMap.has('/activities/search')).toBe(false);
    expect(realPageRouteMetaMap.has('/payments/status')).toBe(false);
  });

  it('removes the retired standalone registration dossier and materials alias', () => {
    expect(realPageRouteMetaMap.has('/competitions/registrations')).toBe(false);
    expect(realPageRouteMetaMap.has('/competitions/:competitionUuid/materials')).toBe(false);
    expect(realPageRouteMetaMap.has('/competitions/:competitionUuid/registrations')).toBe(true);
  });

  it('registers participant review results separately from the expert workbench', () => {
    expect(resolveCanonicalRoutePath('/competitions/review-results'))
      .toBe('/competitions/review-results');
    expect(realPageRouteMetaMap.get('/competitions/review-results')?.access)
      .toBe('canVisitCompetitionReviewResults');
  });

  it('keeps certificate generation, batches, and records as distinct competition workspace pages', () => {
    const competitionGroup = backendRoutes.find((route) => route.path === '/competitions');
    const workspaceGroup = competitionGroup?.routes?.find((route) => route.path === '/competitions/:competitionUuid');

    expect(workspaceGroup?.routes?.find((route) => route.path.endsWith('/certificates/generate'))?.component)
      .toBe('@/pages/certificates/GeneratePage');
    expect(workspaceGroup?.routes?.find((route) => route.path.endsWith('/certificates/batches'))?.component)
      .toBe('@/pages/certificates/BatchesPage');
    expect(workspaceGroup?.routes?.find((route) => route.path.endsWith('/certificates/records'))?.component)
      .toBe('@/pages/certificates/RecordsPage');
  });

  it('registers the built-in mock checkout as a hidden guarded page', () => {
    expect(realPageRouteMetaMap.get('/mock-payment/checkout')).toMatchObject({
      access: 'canUseBuiltinMockPayment',
      hideInMenu: true,
    });
  });

  it('registers the review workbench independently from workflow approvals', () => {
    expect(realPageRouteMetaMap.get('/expert-review/reviews')?.access)
      .toBe('canVisitReviewWorkbench');
  });

  it('keeps expert management and expert application in the unified review navigation', () => {
    expect(backendRouteMeta.find((item) => item.path === '/experts')).toBeUndefined();
    expect(backendRouteMeta.find((item) => item.path === '/experts/management')?.name)
      .toBe('nav.experts.management');
    expect(backendRouteMeta.find((item) => item.path === '/competitions/expert-apply')?.name)
      .toBe('nav.competitions.expertApply');
    expect(backendRoutes.find((route) => route.path === '/experts'))
      .toMatchObject({ hideInMenu: true });
  });

  it('redirects the retired standalone expert query page to expert management', () => {
    expect(resolveCanonicalRoutePath('/experts/query')).toBe('/experts/management');
    expect(resolveCanonicalRoutePath('/experts/query/')).toBe('/experts/management');
    expect(realPageRouteMetaMap.has('/experts/query')).toBe(false);
    expect(backendRoutes.find((route) => route.path === '/experts')?.routes?.find((route) => route.path === '/experts/query'))
      .toMatchObject({ redirect: '/experts/management', hideInMenu: true });
  });

  it('keeps workflow configuration in system settings with a legacy redirect', () => {
    expect(resolveCanonicalRoutePath('/workflows/config')).toBe('/settings/workflows');
    expect(realPageRouteMetaMap.get('/settings/workflows')?.access).toBe('canVisitWorkflowConfig');
  });

  it('keeps settings as a guarded route group with page-level access keys', () => {
    expect(systemRouteMeta.find((item) => item.path === '/settings')?.access).toBe('canVisitSettings');

    const settingsRoute = systemRoutes.find((item) => item.path === '/settings');
    expect(settingsRoute?.access).toBe('canVisitSettings');
    expect(settingsRoute?.routes?.find((item) => item.path === '/settings/dicts')?.access)
      .toBe('canVisitSystemDicts');
    expect(settingsRoute?.routes?.find((item) => item.path === '/settings/payment')?.access)
      .toBe('canVisitSystemPayment');
  });

  it.each([
    ['/localization', '/settings/localization'],
    ['/settings/monitoring/api-docs', '/settings/api-docs'],
    ['/settings/monitoring/audit', '/settings/audit'],
  ])('keeps legacy route %s as a real redirect to %s', (legacyPath, targetPath) => {
    const route = systemRoutes.find((item) => item.path === legacyPath);
    expect(route?.redirect).toBe(targetPath);
  });
});
