import { beforeEach, describe, expect, it } from "vitest";
import {
  clearStoredToken,
  getStoredToken,
  getStoredUserId,
  setStoredToken,
  setStoredUserId
} from "./storage";

describe("auth storage", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("should persist and clear token", () => {
    setStoredToken("abc");
    expect(getStoredToken()).toBe("abc");

    clearStoredToken();
    expect(getStoredToken()).toBe("");
  });

  it("should persist user id independently", () => {
    setStoredUserId("u-1");
    expect(getStoredUserId()).toBe("u-1");
  });
});
