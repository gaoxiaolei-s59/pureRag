import { getStoredToken } from "../modules/auth/storage";

export type ApiResult<T> = {
  code: string;
  data: T;
  message: string;
  requestId?: string | null;
  success: boolean;
};

export type PageResult<T> = {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
};

const API_PREFIX = "/api";

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = getStoredToken();

  if (token) {
    headers.set("s-token", token);
  }

  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${API_PREFIX}${path}`, {
    ...init,
    headers
  });

  const contentType = response.headers.get("Content-Type") ?? "";
  const payload = contentType.includes("application/json")
    ? ((await response.json()) as ApiResult<T>)
    : ({ success: response.ok, data: undefined, message: await response.text() } as ApiResult<T>);

  if (!response.ok || payload.success === false) {
    throw new Error(payload.message || `请求失败：${response.status}`);
  }

  return payload.data;
}

export function buildApiUrl(path: string) {
  return `${API_PREFIX}${path}`;
}
