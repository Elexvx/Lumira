import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { RecentRegistrationExportDownloadButton } from "./RecentRegistrationExportDownloadButton";

describe("RecentRegistrationExportDownloadButton", () => {
  it("keeps the latest generated filename visible as a persistent retry action", () => {
    const markup = renderToStaticMarkup(
      <RecentRegistrationExportDownloadButton
        download={{
          downloadPath: "/v1/files/42/download",
          fileName: "competition-registration-export.xlsx",
        }}
        busy={false}
        loading={false}
        onDownload={vi.fn()}
      />,
    );

    expect(markup).toContain("重新下载最近文件");
    expect(markup).toContain(
      'title="重新下载：competition-registration-export.xlsx"',
    );
    expect(markup).not.toContain("disabled");
  });
});
