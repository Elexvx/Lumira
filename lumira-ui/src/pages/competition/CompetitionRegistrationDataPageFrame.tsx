import type { PropsWithChildren } from "react";
import { CompetitionWorkspacePageFrame } from "@/features/competition-workspace/CompetitionWorkspacePageFrame";

type CompetitionRegistrationDataPageFrameProps = PropsWithChildren;

export const CompetitionRegistrationDataPageFrame = ({
  children,
}: CompetitionRegistrationDataPageFrameProps) => {
  return (
    <CompetitionWorkspacePageFrame
      embeddedInWorkspace
      title="报名与材料"
      showWorkspaceHeader
      breadcrumb={{
        items: [
          {
            key: "data-management",
            title: "数据管理",
            path: "/data-management",
          },
          { key: "competition-registrations", title: "报名与材料" },
        ],
      }}
      workspaceVariant="table"
      workspaceBodyClassName="competition-registration-data-page--workspace"
    >
      {children}
    </CompetitionWorkspacePageFrame>
  );
};
