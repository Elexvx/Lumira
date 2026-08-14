import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  requestFile: vi.fn(),
  saveBlobAsFile: vi.fn(),
}));

vi.mock("@/services/common/request", () => ({
  requestFile: mocks.requestFile,
}));

vi.mock("@/utils/download", () => ({
  saveBlobAsFile: mocks.saveBlobAsFile,
}));

import {
  createReadyRegistrationExportDownload,
  downloadReadyRegistrationExport,
} from "./registrationExportDownload";

describe("registration export download", () => {
  beforeEach(() => {
    mocks.requestFile.mockReset();
    mocks.saveBlobAsFile.mockReset();
  });

  it("normalizes an API download URL while retaining its query string", () => {
    expect(
      createReadyRegistrationExportDownload(
        " /api/v1/files/42/download?scope=download-center ",
        " registrations.xlsx ",
      ),
    ).toEqual({
      downloadPath: "/v1/files/42/download?scope=download-center",
      fileName: "registrations.xlsx",
    });
  });

  it("fetches and saves a ready export so it can be downloaded again", async () => {
    const blob = new Blob(["xlsx"], {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    mocks.requestFile.mockResolvedValue(blob);

    await downloadReadyRegistrationExport({
      downloadPath: "/v1/files/42/download",
      fileName: "registrations.xlsx",
    });

    expect(mocks.requestFile).toHaveBeenCalledWith("/v1/files/42/download", {
      method: "GET",
      silent: true,
    });
    expect(mocks.saveBlobAsFile).toHaveBeenCalledWith(blob, "registrations.xlsx");
  });
});
