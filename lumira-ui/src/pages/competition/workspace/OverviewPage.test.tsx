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
      allowedModules: ['overview', 'registrations'],
    },
    canOpen: (module: string) => ['overview', 'registrations'].includes(module),
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

    expect(markup).toContain('data-page-title="赛事概览"');
    expect(markup).not.toContain('赛事工作空间');
    expect(markup.indexOf('赛事名称')).toBeLessThan(markup.indexOf('赛事代码'));
    expect(markup.indexOf('赛事名称')).toBeLessThan(markup.indexOf('赛事编号'));
    expect(markup).toContain('工作空间兼容性测试赛事');
    expect(markup).toContain('<dl class="competition-workspace-overview__details">');
    expect(markup.match(/competition-workspace-overview__detail--wide/g)).toHaveLength(2);
    expect(markup).toContain('已发布');
    expect(markup).not.toContain('>published<');
  });

  it('uses specific accessible module actions instead of repeated generic links', () => {
    const markup = renderToStaticMarkup(<OverviewPage />);

    expect(markup).toContain('competition-workspace-overview__module-tile');
    expect(markup).toContain('aria-label="进入报名与材料"');
    expect(markup).toContain('筛选报名团队、核对完整资料并按范围导出。');
    expect(markup).not.toContain('>进入模块<');
  });
});
