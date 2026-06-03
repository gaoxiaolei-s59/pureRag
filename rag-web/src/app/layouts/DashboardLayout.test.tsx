import { afterEach, describe, expect, it } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { renderToStaticMarkup } from "react-dom/server";
import { clearAuthStorage, setStoredToken, setStoredUserId, setStoredUserName } from "../../modules/auth/storage";
import { DashboardLayout } from "./DashboardLayout";

describe("DashboardLayout", () => {
  afterEach(() => {
    clearAuthStorage();
    localStorage.removeItem("pureagent-app-theme");
    localStorage.removeItem("pureagent-chat-theme");
  });

  it("should follow the shared dark theme used by the chat page", () => {
    setStoredToken("test-token");
    setStoredUserId("user-1");
    setStoredUserName("admin");
    localStorage.setItem("pureagent-app-theme", "dark");

    const html = renderToStaticMarkup(
      <MemoryRouter>
        <DashboardLayout />
      </MemoryRouter>
    );

    expect(html).toContain('class="dashboard-shell" data-theme="dark"');
    expect(html).toContain("切换浅色模式");
  });
});
