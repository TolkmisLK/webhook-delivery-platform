import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiRequestError } from "../src/lib/api";

afterEach(() => {
  api.resetSessionState();
  vi.unstubAllGlobals();
});

describe("operator session access", () => {
  it("obtains csrf metadata before login and refreshes it afterward", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ headerName: "X-CSRF-TOKEN", token: "before-login" }),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => ({ username: "admin" }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ headerName: "X-CSRF-TOKEN", token: "after-login" }),
      });
    vi.stubGlobal("fetch", fetchMock);

    await expect(api.login("admin", "local-admin-password")).resolves.toEqual({
      username: "admin",
    });
    expect(fetchMock).toHaveBeenNthCalledWith(1, "/api/auth/csrf", {
      headers: { "Content-Type": "application/json" },
    });
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": "before-login" },
      body: JSON.stringify({ username: "admin", password: "local-admin-password" }),
    });
    expect(fetchMock).toHaveBeenNthCalledWith(3, "/api/auth/csrf", {
      headers: { "Content-Type": "application/json" },
    });
  });

  it("preserves stable authentication error metadata", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: async () => ({ code: "unauthenticated", message: "Authentication is required" }),
      }),
    );

    const error = await api.getSession().catch((caught) => caught);

    expect(error).toBeInstanceOf(ApiRequestError);
    expect(error).toMatchObject({ status: 401, code: "unauthenticated" });
  });
});
