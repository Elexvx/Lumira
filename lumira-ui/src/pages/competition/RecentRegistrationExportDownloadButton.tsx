import { DownloadOutlined } from "@ant-design/icons";
import { Button } from "antd";
import type { ReadyRegistrationExportDownload } from "./registrationExportDownload";

type RecentRegistrationExportDownloadButtonProps = {
  download: ReadyRegistrationExportDownload;
  busy: boolean;
  loading: boolean;
  onDownload: () => void;
};

export const RecentRegistrationExportDownloadButton = ({
  download,
  busy,
  loading,
  onDownload,
}: RecentRegistrationExportDownloadButtonProps) => (
  <Button
    icon={<DownloadOutlined aria-hidden />}
    disabled={busy}
    loading={loading}
    title={`重新下载：${download.fileName}`}
    onClick={onDownload}
  >
    重新下载最近文件
  </Button>
);
