export const formatBytes = (value?: number | null) => {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return '-';
  }
  if (value === 0) {
    return '0 B';
  }

  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = value;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }
  return `${size >= 100 || unitIndex === 0 ? size.toFixed(0) : size.toFixed(2)} ${units[unitIndex]}`;
};

export const formatPercent = (value?: number | null, digits = 2) => {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return '-';
  }
  return `${value.toFixed(digits)}%`;
};

export const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN', { hour12: false });
};

export const formatDuration = (seconds?: number | null) => {
  if (seconds === undefined || seconds === null || Number.isNaN(seconds)) {
    return '-';
  }
  const totalSeconds = Math.max(0, Math.floor(seconds));
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const secs = totalSeconds % 60;
  if (days > 0) {
    return `${days}天${hours}小时${minutes}分钟`;
  }
  if (hours > 0) {
    return `${hours}小时${minutes}分钟`;
  }
  if (minutes > 0) {
    return `${minutes}分钟${secs}秒`;
  }
  return `${secs}秒`;
};

export const formatNumber = (value?: number | null) => {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return '-';
  }
  return value.toLocaleString('zh-CN');
};
