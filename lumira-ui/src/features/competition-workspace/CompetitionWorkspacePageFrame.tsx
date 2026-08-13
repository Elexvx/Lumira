import { Typography } from 'antd';
import type { ComponentProps, PropsWithChildren, ReactNode } from 'react';
import { ManagementPage } from '@/features/management/ManagementPage';
import { ManagementPageBody } from '@/features/management/ManagementPageBody';

type WorkspacePageVariant = 'content' | 'flush' | 'table';
type ManagementPageProps = ComponentProps<typeof ManagementPage>;

interface CompetitionWorkspacePageFrameProps extends PropsWithChildren {
  embeddedInWorkspace: boolean;
  title: ReactNode;
  extra?: ReactNode;
  description?: ReactNode;
  breadcrumb?: ManagementPageProps['breadcrumb'];
  bodyClassName?: string;
  workspaceBodyClassName?: string;
  workspaceVariant?: WorkspacePageVariant;
  showWorkspaceHeader?: boolean;
}

const mergeClassName = (...classNames: Array<string | undefined>) => classNames.filter(Boolean).join(' ');

export const CompetitionWorkspacePageFrame = ({
  bodyClassName,
  breadcrumb,
  children,
  description,
  embeddedInWorkspace,
  extra,
  showWorkspaceHeader = false,
  title,
  workspaceBodyClassName,
  workspaceVariant = 'content',
}: CompetitionWorkspacePageFrameProps) => {
  if (embeddedInWorkspace) {
    const hasWorkspaceHeader = showWorkspaceHeader || Boolean(extra) || Boolean(description);

    return (
      <ManagementPageBody
        className={mergeClassName(
          'saas-management-page',
          'competition-workspace-module-page',
          `competition-workspace-module-page--${workspaceVariant}`,
          bodyClassName,
          workspaceBodyClassName,
        )}
      >
        {hasWorkspaceHeader ? (
          <div className="competition-workspace-module-page__toolbar">
            <div className="competition-workspace-module-page__heading">
              <Typography.Title level={4} className="competition-workspace-module-page__title">
                {title}
              </Typography.Title>
              {description ? (
                <Typography.Paragraph
                  type="secondary"
                  className="competition-workspace-module-page__description"
                >
                  {description}
                </Typography.Paragraph>
              ) : null}
            </div>
            {extra ? <div className="competition-workspace-module-page__actions">{extra}</div> : null}
          </div>
        ) : null}
        {children}
      </ManagementPageBody>
    );
  }

  return (
    <ManagementPage title={title} extra={extra} breadcrumb={breadcrumb}>
      <ManagementPageBody className={bodyClassName}>
        {description ? <Typography.Paragraph type="secondary">{description}</Typography.Paragraph> : null}
        {children}
      </ManagementPageBody>
    </ManagementPage>
  );
};
