import type { PropsWithChildren } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/features/management/ManagementPage", () => ({
  ManagementPage: ({ children }: PropsWithChildren) => (
    <section data-management-page="true">{children}</section>
  ),
}));

vi.mock("@/features/management/ManagementPageBody", () => ({
  ManagementPageBody: ({
    children,
    className,
  }: PropsWithChildren<{ className?: string }>) => (
    <div className={["saas-management-page-body", className].filter(Boolean).join(" ")}>
      {children}
    </div>
  ),
}));

import { CompetitionRegistrationDataPageFrame } from "./CompetitionRegistrationDataPageFrame";

describe("CompetitionRegistrationDataPageFrame", () => {
  it("embeds workspace content without nesting another page container", () => {
    const markup = renderToStaticMarkup(
      <CompetitionRegistrationDataPageFrame>
        <div>workspace content</div>
      </CompetitionRegistrationDataPageFrame>,
    );

    expect(markup).toContain("saas-management-page-body");
    expect(markup).toContain("competition-registration-data-page--workspace");
    expect(markup).toContain("competition-workspace-module-page__toolbar");
    expect(markup).toContain("报名与材料");
    expect(markup).not.toContain("筛选报名团队");
    expect(markup).not.toContain("查询结果");
    expect(markup).not.toContain("最多选择");
    expect(markup).not.toContain("data-management-page");
  });
});
