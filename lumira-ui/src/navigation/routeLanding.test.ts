import { describe, expect, it } from 'vitest';
import {
  resolveDataManagementLandingPath,
  resolveExpertReviewLandingPath,
  resolvePersonalCenterLandingPath,
  resolveUserCenterLandingPath,
  resolveWorkflowLandingPath,
} from './routeLanding';

describe('route landing', () => {
  it('selects a data-management page the current role can access', () => {
    expect(resolveDataManagementLandingPath({ canVisitPaymentOrders: true })).toBe('/payments/management');
    expect(resolveDataManagementLandingPath({ canVisitCertificateTemplates: true })).toBe('/certificates/templates');
    expect(resolveDataManagementLandingPath({ canVisitDownloadCenter: true })).toBe('/data-management/download-center');
  });

  it('does not send expert-only users to the review workbench', () => {
    expect(resolveExpertReviewLandingPath({ canVisitExperts: true })).toBe('/experts/management');
  });

  it('sends reviewer-only users to their task page', () => {
    expect(resolveExpertReviewLandingPath({ canVisitReviewTasks: true })).toBe('/expert-review/reviews');
  });

  it('does not send workflow config users to approval tasks', () => {
    expect(resolveWorkflowLandingPath({ canVisitWorkflowConfig: true })).toBe('/settings/workflows');
  });

  it('supports department-only and file-only user-center roles', () => {
    expect(resolveUserCenterLandingPath({ canVisitSystemDepartments: true })).toBe('/user-center/departments');
    expect(resolvePersonalCenterLandingPath({ canVisitSystemMyFiles: true })).toBe('/user-center/personal-center/files');
  });
});
