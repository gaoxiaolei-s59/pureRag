import { afterEach, describe, expect, it } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { renderToStaticMarkup } from "react-dom/server";
import { clearAuthStorage, setStoredToken, setStoredUserId, setStoredUserName } from "../auth/storage";
import { ChatPage } from "./pages/ChatPage";

describe("ChatPage", () => {
  afterEach(() => {
    clearAuthStorage();
  });

  it("should keep the chat route as the conversational home instead of a dashboard panel", () => {
    setStoredToken("test-token");
    setStoredUserId("user-1");
    setStoredUserName("admin");

    const html = renderToStaticMarkup(
      <MemoryRouter>
        <ChatPage />
      </MemoryRouter>
    );

    expect(html).toContain("把问题变成");
    expect(html).toContain("清晰答案");
    expect(html).toContain("PureAgent");
    expect(html).not.toContain("Ragent AI");
    expect(html).toContain("管理后台");
    expect(html).toContain("搜索对话");
    expect(html).not.toContain("聊天工作区");
  });
});
