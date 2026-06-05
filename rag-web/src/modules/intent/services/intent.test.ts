import { beforeEach, describe, expect, it, vi } from "vitest";
import { deleteIntentNode, updateIntentNode } from "./intent";

const requestMock = vi.hoisted(() => vi.fn());

vi.mock("../../../services/http", () => ({
  request: requestMock
}));

describe("intent services", () => {
  beforeEach(() => {
    requestMock.mockReset();
  });

  it("updateIntentNode should call the intent-tree update endpoint with the CRUD id", async () => {
    requestMock.mockResolvedValue(undefined);

    await updateIntentNode("db-1", {
      intentCode: "group-hr",
      name: "人事服务",
      level: 1,
      kind: 0,
      enabled: 1
    });

    expect(requestMock).toHaveBeenCalledWith("/intent-tree/db-1", {
      method: "PUT",
      body: JSON.stringify({
        intentCode: "group-hr",
        name: "人事服务",
        level: 1,
        kind: 0,
        enabled: 1
      })
    });
  });

  it("deleteIntentNode should call the intent-tree delete endpoint with the CRUD id", async () => {
    requestMock.mockResolvedValue(undefined);

    await deleteIntentNode("db-1");

    expect(requestMock).toHaveBeenCalledWith("/intent-tree/db-1", {
      method: "DELETE"
    });
  });
});
