export const DEFAULT_CHUNK_CONFIG = JSON.stringify({ chunkSize: 512, overlapSize: 128 }, null, 2);

export function formatBytes(value?: number) {
  if (!value) {
    return "-";
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

export function formatDateTime(value?: string) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  }).format(date);
}

export function statusText(status?: string) {
  const map: Record<string, string> = {
    pending: "待处理",
    running: "处理中",
    failed: "失败",
    success: "完成"
  };
  return status ? map[status] ?? status : "-";
}
