import { test, expect } from "@playwright/test";

const endpoints = [
  { id: "endpoint-a", name: "Receiver A", url: "https://example.org/a", active: true, version: 0, createdAt: "2026-10-15T00:00:00Z" },
  { id: "endpoint-b", name: "Receiver B", url: "https://example.org/b", active: true, version: 0, createdAt: "2026-10-15T00:00:00Z" },
];

function delivery(id: string, status: "SUCCEEDED" | "DEAD", endpointId: string) {
  return {
    id, eventId: `${id}-event`, eventType: `${id}.event`,
    endpointName: endpointId === "endpoint-a" ? "Receiver A" : "Receiver B",
    endpointUrl: endpointId === "endpoint-a" ? "https://example.org/a" : "https://example.org/b",
    status, attemptCount: 1, maxAttempts: 3, nextAttemptAt: "2026-10-15T00:00:00Z",
    lastStatusCode: status === "SUCCEEDED" ? 200 : 500, lastError: null,
    createdAt: "2026-10-15T00:00:00Z", updatedAt: "2026-10-15T00:00:00Z",
  };
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    window.EventSource = class { addEventListener() {} close() {} } as unknown as typeof EventSource;
  });
});

test("history pages and filters are sent to the server and empty results are clear", async ({ page }) => {
  const requests: URL[] = [];
  await page.route("**/api/**", async route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const data = path.endsWith("/csrf") ? { headerName: "X-CSRF-TOKEN", token: "fixture-token" }
      : path.endsWith("/session") ? { username: "admin" }
      : path.endsWith("/endpoints") ? endpoints
      : path.endsWith("/stats") ? { total: 26, byStatus: { SUCCEEDED: 25, DEAD: 1, PENDING: 0, PROCESSING: 0, RETRY_SCHEDULED: 0, CANCELED: 0 } }
      : path.endsWith("/deliveries/page") ? (() => {
          requests.push(url);
          const status = url.searchParams.get("status");
          const endpointId = url.searchParams.get("endpointId");
          const pageNumber = Number(url.searchParams.get("page"));
          const all = [delivery("dead-a", "DEAD", "endpoint-a"), ...Array.from({ length: 24 }, (_, index) => delivery(`ok-${index}`, "SUCCEEDED", "endpoint-a")), delivery("last-b", "SUCCEEDED", "endpoint-b")];
          const filtered = all.filter(item => (!status || item.status === status) && (!endpointId || item.endpointName === (endpointId === "endpoint-a" ? "Receiver A" : "Receiver B")));
          return { items: filtered.slice(pageNumber * 25, (pageNumber + 1) * 25), page: pageNumber, size: 25, hasNext: filtered.length > (pageNumber + 1) * 25 };
        })() : [];
    await route.fulfill({ json: data });
  });
  await page.goto("/");
  await expect(page.getByText("Page 1")).toBeVisible();
  await page.getByRole("button", { name: "Next" }).click();
  await expect(page.getByText("last-b.event")).toBeVisible();
  await expect(page.getByText("Page 2")).toBeVisible();
  await page.getByLabel("Status filter").selectOption("DEAD");
  await expect(page.getByText("dead-a.event")).toBeVisible();
  await expect(page.getByText("Page 1")).toBeVisible();
  await page.getByLabel("Endpoint filter").selectOption("endpoint-b");
  await expect(page.getByText("No deliveries match these filters.")).toBeVisible();
  expect(requests.some(url => url.searchParams.get("page") === "1" && url.searchParams.get("size") === "25")).toBeTruthy();
  expect(requests.some(url => url.searchParams.get("status") === "DEAD" && url.searchParams.get("endpointId") === "endpoint-b")).toBeTruthy();
});

test("a slower previous page cannot overwrite a newer filtered response", async ({ page }) => {
  let releaseOld!: () => void;
  let oldStarted!: () => void;
  let oldFinished!: () => void;
  const blocked = new Promise<void>(resolve => { releaseOld = resolve; });
  const started = new Promise<void>(resolve => { oldStarted = resolve; });
  const finished = new Promise<void>(resolve => { oldFinished = resolve; });
  await page.route("**/api/**", async route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (path.endsWith("/deliveries/page")) {
      if (url.searchParams.get("page") === "1" && !url.searchParams.has("status")) {
        oldStarted();
        await blocked;
        await route.fulfill({ json: { items: [delivery("stale", "SUCCEEDED", "endpoint-b")], page: 1, size: 25, hasNext: false } });
        oldFinished();
        return;
      }
      const filtered = url.searchParams.get("status") === "DEAD";
      await route.fulfill({ json: { items: [delivery(filtered ? "fresh-dead" : "initial", filtered ? "DEAD" : "SUCCEEDED", "endpoint-a")], page: 0, size: 25, hasNext: !filtered } });
      return;
    }
    const data = path.endsWith("/csrf") ? { headerName: "X-CSRF-TOKEN", token: "fixture-token" }
      : path.endsWith("/session") ? { username: "admin" }
      : path.endsWith("/endpoints") ? endpoints
      : path.endsWith("/stats") ? { total: 2, byStatus: { SUCCEEDED: 1, DEAD: 1, PENDING: 0, PROCESSING: 0, RETRY_SCHEDULED: 0, CANCELED: 0 } } : [];
    await route.fulfill({ json: data });
  });
  await page.goto("/");
  await expect(page.getByText("initial.event")).toBeVisible();
  await page.getByRole("button", { name: "Next" }).click();
  await started;
  await page.getByLabel("Status filter").selectOption("DEAD");
  await expect(page.getByText("fresh-dead.event")).toBeVisible();
  releaseOld();
  await finished;
  await page.waitForTimeout(150);
  await expect(page.getByText("fresh-dead.event")).toBeVisible();
  await expect(page.getByText("stale.event")).toHaveCount(0);
});
