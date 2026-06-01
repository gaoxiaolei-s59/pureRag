import { getStoredToken } from "../modules/auth/storage";

export function useAuthGuard() {
  return {
    isAuthenticated: Boolean(getStoredToken()),
    token: getStoredToken()
  };
}
