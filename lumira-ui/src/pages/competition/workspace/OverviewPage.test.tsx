import type { PropsWithChildren, ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';

vi.mock('@/features/competition-workspace/CompetitionWorkspaceContext', () => ({
  useCompetitionWorkspace: () => ({
    workspace: {
      competitionUuid: '9af5fde3-1078-4509-91b8-11a77ed4756f',
      competitionNo: '202608122342108411',
      code: 'workspace-2026',
      title: '工作空间兼容性测试赛事',
      status: 'published',
      activeRegistrationCount: 3,
      capabilities: [],
      allowedModules: ['overview'],
    },
    canOpen: (module: string) => module === 'overview',
    navigateToModule: vi.fn(),
  }),
}));

vi.mock('@/features/competition-workspace/CompetitionWorkspacePageFrame', () => ({
  CompetitionWorkspacePageFrame: ({
    children,
    title,
  }: PropsWithChildren<{ title: ReactNode }>) => (
    <section data-page-title={title}>{children}</section>
  ),
}));

import OverviewPage from './OverviewPage';

describe('Competition workspace overview', () => {
  it('renders localized competition details in the requested order', () => {
    const markup = renderToStaticMarkup(<OverviewPage />);

    expect(markup).toContain('data-page-title="概览"');
    expect(markup).not.toContain('赛事工作空间');
    expect(markup.indexOf('赛事名称')).toBeLessThan(markup.indexOf('赛事代码'));
    expect(markup).toContain('工作空间兼容性测试赛事');
    expect(markup).toContain('已发布');
    expect(markup).not.toContain('>published<');
  });

  it('keeps module actions left aligned and removes header arrows', () => {
    const markup = renderToStaticMarkup(<OverviewPage />);

    expect(markup).toContain('competition-workspace-overview__module-action');
    expect(markup).toContain('进入模块');
    expect(markup).not.toContain('anticon-arrow-right');
  });
});
