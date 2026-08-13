import type { PropsWithChildren } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';

vi.mock('@/features/management/ManagementPage', () => ({
  ManagementPage: ({ children }: PropsWithChildren) => (
    <section data-management-page="true">{children}</section>
  ),
}));

vi.mock('@/features/management/ManagementPageBody', () => ({
  ManagementPageBody: ({
    children,
    className,
  }: PropsWithChildren<{ className?: string }>) => (
    <div className={['saas-management-page-body', className].filter(Boolean).join(' ')}>
      {children}
    </div>
  ),
}));

import { CompetitionWorkspacePageFrame } from './CompetitionWorkspacePageFrame';

describe('CompetitionWorkspacePageFrame', () => {
  it('uses the shared embedded table frame without nesting a page container', () => {
    const markup = renderToStaticMarkup(
      <CompetitionWorkspacePageFrame embeddedInWorkspace title="Workspace table" workspaceVariant="table">
        <div>table</div>
      </CompetitionWorkspacePageFrame>,
    );

    expect(markup).toContain('competition-workspace-module-page--table');
    expect(markup).not.toContain('data-management-page');
    expect(markup).not.toContain('competition-workspace-module-page__toolbar');
  });

  it('keeps required actions in a reusable embedded toolbar', () => {
    const markup = renderToStaticMarkup(
      <CompetitionWorkspacePageFrame
        embeddedInWorkspace
        title="Workspace settings"
        extra={<button type="button">Save</button>}
        workspaceVariant="flush"
      >
        <div>settings</div>
      </CompetitionWorkspacePageFrame>,
    );

    expect(markup).toContain('competition-workspace-module-page__toolbar');
    expect(markup).toContain('Workspace settings');
    expect(markup).toContain('Save');
  });

  it('preserves the full management page outside a workspace', () => {
    const markup = renderToStaticMarkup(
      <CompetitionWorkspacePageFrame embeddedInWorkspace={false} title="Standalone page">
        <div>standalone</div>
      </CompetitionWorkspacePageFrame>,
    );

    expect(markup).toContain('data-management-page="true"');
    expect(markup).not.toContain('competition-workspace-module-page');
  });
});
