import { requestFile } from "@/services/common/request";
import { saveBlobAsFile } from "@/utils/download";

export type ReadyRegistrationExportDownload = {
  downloadPath: string;
  fileName: string;
};

export const createReadyRegistrationExportDownload = (
  downloadUrl: string,
  fileName: string,
): ReadyRegistrationExportDownload => ({
  downloadPath: downloadUrl.trim().replace(/^\/api(?=\/)/, ""),
  fileName: fileName.trim(),
});

export const downloadReadyRegistrationExport = async (
  download: ReadyRegistrationExportDownload,
): Promise<void> => {
  const blob = await requestFile(download.downloadPath, {
    method: "GET",
    silent: true,
  });
  saveBlobAsFile(blob, download.fileName);
};
