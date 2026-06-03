import { afterEach, describe, expect, it } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { renderToStaticMarkup } from "react-dom/server";
import { clearAuthStorage, setStoredToken, setStoredUserId, setStoredUserName } from "../auth/storage";
import { KnowledgePage } from "./pages/KnowledgePage";

describe("KnowledgePage", () => {
  afterEach(() => {
    clearAuthStorage();
  });

  it("should not display owner information in the knowledge base overview", () => {
    setStoredToken("test-token");
    setStoredUserId("user-1");
    setStoredUserName("admin");

    const html = renderToStaticMarkup(
      <MemoryRouter>
        <KnowledgePage />
      </MemoryRouter>
    );

    expect(html).toContain("知识库管理");
    expect(html).not.toContain("负责人");
    expect(html).not.toContain("负责人数量");
  });
});
