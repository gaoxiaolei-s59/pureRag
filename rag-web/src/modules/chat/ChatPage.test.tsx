import { afterEach, describe, expect, it } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { renderToStaticMarkup } from "react-dom/server";
import { clearAuthStorage, setStoredToken, setStoredUserId, setStoredUserName } from "../auth/storage";
import { ChatPage } from "./pages/ChatPage";

describe("ChatPage", () => {
  afterEach(() => {
    clearAuthStorage();
  });

  it("should render a PureAgent conversational home with a theme option", () => {
    setStoredToken("test-token");
    setStoredUserId("user-1");
    setStoredUserName("admin");

    const html = renderToStaticMarkup(
      <MemoryRouter>
        <ChatPage />
      </MemoryRouter>
    );

    expect(html).toContain("PureAgent 知识问答");
    expect(html).toContain("连接知识库、意图识别与深度检索");
    expect(html).toContain("PureAgent");
    expect(html).toContain("切换深色模式");
    expect(html).not.toContain("使用快速模式开始对话");
    expect(html).not.toContain("快速模式");
    expect(html).not.toContain("专家模式");
    expect(html).not.toContain("识图模式");
    expect(html).not.toContain("Ragent AI");
    expect(html).toContain("管理后台");
    expect(html).toContain("搜索对话");
    expect(html).not.toContain("聊天工作区");
  });

  it("should keep the empty landing view spacious without starter cards", () => {
    setStoredToken("test-token");
    setStoredUserId("user-1");
    setStoredUserName("admin");

    const html = renderToStaticMarkup(
      <MemoryRouter>
        <ChatPage />
      </MemoryRouter>
    );

    expect(html).not.toContain("内容总结");
    expect(html).not.toContain("任务拆解");
    expect(html).not.toContain("灵感扩展");
    expect(html).not.toContain("试试这些开场");
    expect(html).not.toContain("推荐问法");
  });

  it("should render composer mode controls as pill buttons without native checkbox squares", () => {
    setStoredToken("test-token");
    setStoredUserId("user-1");
    setStoredUserName("admin");

    const html = renderToStaticMarkup(
      <MemoryRouter>
        <ChatPage />
      </MemoryRouter>
    );

    expect(html).toContain("thinking-pill inactive");
    expect(html).toContain('aria-pressed="false"');
    expect(html).toContain("深度思考");
    expect(html).toContain("智能搜索");
    expect(html).not.toContain('type="checkbox"');
  });
});
