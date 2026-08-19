import { describe, expect, it } from 'vitest';
import {
  initialReviewRosterExpertIds,
  shouldLoadReviewAwardGrants,
  shouldReloadReviewPlans,
  shouldShowGlobalExpertTasks,
  shouldShowReviewAdminWorkbench,
} from './reviewWorkspaceBehavior';

describe('competition review workspace behavior', () => {
  it('reloads plans with a workspace UUID even when the numeric competition id is intentionally absent', () => {
    expect(shouldReloadReviewPlans({
      canManagePlans: true,
      workspaceUuid: 'c8c3ca4d-87b7-4c2a-81b6-0c538c700001',
      stageId: 12,
    })).toBe(true);
  });

  it('still reloads plans in the global workbench with a numeric competition id', () => {
    expect(shouldReloadReviewPlans({
      canManagePlans: true,
      competitionId: 42,
      stageId: 12,
    })).toBe(true);
  });

  it('requires both plan-management permission and a stage', () => {
    expect(shouldReloadReviewPlans({ canManagePlans: false, competitionId: 42, stageId: 12 })).toBe(false);
    expect(shouldReloadReviewPlans({ canManagePlans: true, competitionId: 42 })).toBe(false);
  });

  it('keeps the cross-competition expert task list out of a selected competition workspace', () => {
    expect(shouldShowGlobalExpertTasks(true, true)).toBe(false);
    expect(shouldShowGlobalExpertTasks(true, false)).toBe(true);
  });

  it('keeps review management inside a selected competition workspace', () => {
    expect(shouldShowReviewAdminWorkbench(true, true)).toBe(true);
    expect(shouldShowReviewAdminWorkbench(true, false)).toBe(false);
    expect(shouldShowReviewAdminWorkbench(false, true)).toBe(false);
  });

  it('does not implicitly select every eligible expert for an empty batch roster', () => {
    expect(initialReviewRosterExpertIds([])).toEqual([]);
    expect(initialReviewRosterExpertIds([{ expertId: 73 }, { expertId: 81 }])).toEqual([73, 81]);
  });

  it('loads certificate award grants only after the review batch is published', () => {
    expect(shouldLoadReviewAwardGrants(true, 'READY')).toBe(false);
    expect(shouldLoadReviewAwardGrants(true, 'IN_REVIEW')).toBe(false);
    expect(shouldLoadReviewAwardGrants(true, 'PUBLISHED')).toBe(true);
    expect(shouldLoadReviewAwardGrants(false, 'PUBLISHED')).toBe(false);
  });
});
