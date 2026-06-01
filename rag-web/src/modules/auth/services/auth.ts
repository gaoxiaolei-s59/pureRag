import { request } from "../../../services/http";

export type LoginResponse = {
  token: string;
  role?: string;
  avatar?: string;
  userId?: string;
};

export function login(userName: string, password: string) {
  return request<LoginResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ userName, password })
  });
}

export function logout() {
  return request<void>("/auth/logout", {
    method: "POST"
  });
}
