const TOKEN_KEY = "rag-web:s-token";
const USER_ID_KEY = "rag-web:user-id";
const USER_NAME_KEY = "rag-web:user-name";

export function getStoredToken() {
  return localStorage.getItem(TOKEN_KEY) ?? "";
}

export function setStoredToken(token: string) {
  const nextToken = token.trim();
  if (nextToken) {
    localStorage.setItem(TOKEN_KEY, nextToken);
    return;
  }
  localStorage.removeItem(TOKEN_KEY);
}

export function clearStoredToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export function getStoredUserId() {
  return localStorage.getItem(USER_ID_KEY) ?? "";
}

export function setStoredUserId(userId: string) {
  const nextUserId = userId.trim();
  if (nextUserId) {
    localStorage.setItem(USER_ID_KEY, nextUserId);
    return;
  }
  localStorage.removeItem(USER_ID_KEY);
}

export function getStoredUserName() {
  return localStorage.getItem(USER_NAME_KEY) ?? "admin";
}

export function setStoredUserName(userName: string) {
  const nextUserName = userName.trim();
  if (nextUserName) {
    localStorage.setItem(USER_NAME_KEY, nextUserName);
    return;
  }
  localStorage.removeItem(USER_NAME_KEY);
}

export function clearAuthStorage() {
  clearStoredToken();
  setStoredUserId("");
  setStoredUserName("");
}
