import { useState } from "react";

export type AppTheme = "light" | "dark";

export const APP_THEME_STORAGE_KEY = "pureagent-app-theme";
const LEGACY_CHAT_THEME_STORAGE_KEY = "pureagent-chat-theme";

export function getInitialAppTheme(): AppTheme {
  if (typeof window === "undefined") {
    return "light";
  }
  const savedTheme = window.localStorage.getItem(APP_THEME_STORAGE_KEY);
  if (savedTheme === "light" || savedTheme === "dark") {
    return savedTheme;
  }
  const legacyChatTheme = window.localStorage.getItem(LEGACY_CHAT_THEME_STORAGE_KEY);
  if (legacyChatTheme === "light" || legacyChatTheme === "dark") {
    window.localStorage.setItem(APP_THEME_STORAGE_KEY, legacyChatTheme);
    return legacyChatTheme;
  }
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function useAppTheme() {
  const [theme, setTheme] = useState<AppTheme>(getInitialAppTheme);

  function toggleTheme() {
    setTheme((current) => {
      const nextTheme = current === "dark" ? "light" : "dark";
      window.localStorage.setItem(APP_THEME_STORAGE_KEY, nextTheme);
      window.localStorage.setItem(LEGACY_CHAT_THEME_STORAGE_KEY, nextTheme);
      return nextTheme;
    });
  }

  return {
    theme,
    isDarkTheme: theme === "dark",
    toggleTheme
  };
}
