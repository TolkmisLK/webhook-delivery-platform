import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    // These UI tests control HTTP responses; live updates are tested separately.
    window.EventSource = class {
      addEventListener() {}
      close() {}
    } as unknown as typeof EventSource;
  });
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    const endpoint = { id: "fixture", name: "Receiver", url: "https://example.org/hooks", active: true, version: 0, createdAt: "2026-09-27T00:00:00Z" };
    const data = path.endsWith("/csrf") ? { headerName: "X-CSRF-TOKEN", token: "fixture-token" }
      : path.endsWith("/session") || path.endsWith("/login") ? { username: "admin" }
      : path.endsWith("/endpoints") ? [endpoint]
      : path.endsWith("/stats") ? { total: 0, byStatus: {} } : [];
    await route.fulfill({ json: data });
  });
  page.on("dialog", dialog => void dialog.accept());
  await page.goto("/");
  await expect(page.getByRole("button", { name: "Edit", exact: true })).toBeVisible();
});

for (const mode of ["edit", "secret"] as const) {
  test(`${mode}: preserve failed input, freeze pending edits, clear only after success`, async ({ page }) => {
    const form = page.locator(mode === "edit" ? ".endpoint-edit-form" : ".secret-rotation-form");
    const url = mode === "edit" ? "**/api/endpoints/fixture" : "**/api/endpoints/fixture/secret";
    let complete!: (status: number) => void;
    await page.route(url, async route => {
      const status = await new Promise<number>(resolve => { complete = resolve; });
      await route.fulfill({ status, json: status === 200 ? {} : { message: "Controlled conflict" } });
    });
    await page.getByRole("button", { name: mode === "edit" ? "Edit" : "Rotate secret", exact: true }).click();
    const input = form.locator("input").first();
    const value = mode === "edit" ? "Changed receiver" : "fixture-signing-secret";
    await input.fill(value);
    await form.getByRole("button", { name: mode === "edit" ? "Save changes" : "Confirm rotation", exact: true }).click();
    await expect(input).toBeDisabled();
    await expect.poll(() => typeof complete).toBe("function");
    complete(409);
    await expect(input).toBeEnabled();
    await expect(input).toHaveValue(value);
    complete = undefined as unknown as typeof complete;
    await form.getByRole("button", { name: mode === "edit" ? "Save changes" : "Confirm rotation", exact: true }).click();
    await expect(input).toBeDisabled();
    await expect.poll(() => typeof complete).toBe("function");
    complete(200);
    await expect(form).toHaveCount(0);
  });
}

for (const reason of ["logout", "expired"] as const) {
  test(`${reason}: signing secrets are cleared before the next login`, async ({ page }) => {
    await page.getByRole("button", { name: "Rotate secret", exact: true }).click();
    await page.getByLabel("New signing secret", { exact: true }).fill("fixture-only-sensitive-draft");
    if (reason === "logout") await page.getByRole("button", { name: "Sign out", exact: true }).click();
    else {
      await page.route("**/api/endpoints/fixture/secret", route => route.fulfill({ status: 401, json: { message: "Session expired" } }));
      await page.getByRole("button", { name: "Confirm rotation", exact: true }).click();
    }
    await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
    await page.getByLabel("Password", { exact: true }).fill("fixture-password");
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await expect(page.getByRole("button", { name: "Rotate secret", exact: true })).toBeVisible();
    await expect(page.locator(".secret-rotation-form")).toHaveCount(0);
    await expect(page.getByLabel("Signing secret", { exact: true })).toHaveValue("");
    await page.getByRole("button", { name: "Rotate secret", exact: true }).click();
    await expect(page.getByLabel("New signing secret", { exact: true })).toHaveValue("");
  });
}
