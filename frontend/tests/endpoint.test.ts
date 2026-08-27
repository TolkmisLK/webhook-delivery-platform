import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../src/lib/api";
import type { Endpoint } from "../src/lib/types";
import { selectActiveEndpoint } from "../src/lib/view";

const endpoints: Endpoint[] = [
  {
    id: "active-1",
    name: "Primary",
    url: "https://example.com/primary",
    active: true,
    version: 2,
    createdAt: "2026-08-25T00:00:00Z",
  },
  {
    id: "inactive-1",
    name: "Paused",
    url: "https://example.com/paused",
    active: false,
    version: 1,
    createdAt: "2026-08-25T00:00:00Z",
  },
  {
    id: "active-2",
    name: "Secondary",
    url: "https://example.com/secondary",
    active: true,
    version: 0,
    createdAt: "2026-08-25T00:00:00Z",
  },
];

afterEach(() => {
  api.resetSessionState();
  vi.unstubAllGlobals();
});

describe("selectActiveEndpoint", () => {
  it("keeps the current selection when it is active", () => {
    expect(selectActiveEndpoint(endpoints, "active-2")).toBe("active-2");
  });

  it("falls back to the first active endpoint", () => {
    expect(selectActiveEndpoint(endpoints, "inactive-1")).toBe("active-1");
  });

  it("returns an empty selection when every endpoint is inactive", () => {
    expect(
      selectActiveEndpoint(
        endpoints.map((endpoint) => ({ ...endpoint, active: false })),
        "active-1",
      ),
    ).toBe("");
  });
});

describe("endpoint secret rotation", () => {
  it("sends the new secret only in the write request", async () => {
    const rotated = { ...endpoints[0], version: 3 };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ headerName: "X-XSRF-TOKEN", token: "csrf-token" }),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => rotated,
      });
    vi.stubGlobal("fetch", fetchMock);

    await expect(api.rotateEndpointSecret("active-1", "replacement-secret", 2)).resolves.toEqual(
      rotated,
    );
    expect(fetchMock).toHaveBeenNthCalledWith(1, "/api/auth/csrf", {
      headers: { "Content-Type": "application/json" },
    });
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/endpoints/active-1/secret", {
      method: "PATCH",
      headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": "csrf-token" },
      body: JSON.stringify({ newSecret: "replacement-secret", expectedVersion: 2 }),
    });
    expect(rotated).not.toHaveProperty("secret");
  });
});

describe("endpoint configuration editing", () => {
  it("sends the complete configuration with the observed version", async () => {
    const updated = {
      ...endpoints[0],
      name: "Updated receiver",
      url: "https://example.com/updated",
      version: 3,
    };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ headerName: "X-XSRF-TOKEN", token: "csrf-token" }),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => updated,
      });
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      api.updateEndpoint("active-1", "Updated receiver", "https://example.com/updated", 2),
    ).resolves.toEqual(updated);
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/endpoints/active-1", {
      method: "PUT",
      headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": "csrf-token" },
      body: JSON.stringify({
        name: "Updated receiver",
        url: "https://example.com/updated",
        expectedVersion: 2,
      }),
    });
  });
});
