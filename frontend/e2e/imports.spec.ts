import { expect, test } from "@playwright/test";

/**
 * Roadmap phase 3: a folder dropped into the import directory shows up on the Imports page; with
 * no plugin it needs manual metadata, then it is imported. CI copies `fixtures/import/oneshot`
 * into the stack's import folder and runs the stack with short polling/stability windows. Runs
 * after auth.spec.ts, which creates the admin.
 */

const ADMIN = { username: "owner", password: "e2e-owner-password" };

test("manual metadata imports a folder that has no plugin", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await page.goto("/login");
  await page.getByLabel("Username").fill(ADMIN.username);
  await page.getByLabel("Password").fill(ADMIN.password);
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(
    page.getByRole("heading", { name: "Your library" }),
  ).toBeVisible();

  await page.goto("/admin/imports?status=attention");
  const folder = page.getByRole("button", { name: "oneshot", exact: true });
  // The poller picks the folder up once it is stable; reload until it is listed.
  await expect(async () => {
    await page.reload();
    await expect(folder).toBeVisible({ timeout: 2_000 });
  }).toPass({ timeout: 60_000 });

  await folder.click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByText(/No metadata plugin supports/)).toBeVisible();
  await dialog.getByRole("button", { name: "Fill metadata manually" }).click();
  await dialog.getByLabel("Title").fill("E2E One Shot");
  await dialog.getByLabel("Type").selectOption("one-shot");
  await dialog.getByLabel("Authors").fill("E2E Author");
  await dialog.getByRole("button", { name: "Save and import" }).click();

  await expect(dialog.getByText("Done", { exact: true })).toBeVisible({
    timeout: 60_000,
  });
  await expect(dialog.getByText("Imported 1 chapter.")).toBeVisible();
  await dialog.getByRole("link", { name: "E2E One Shot" }).click();

  await expect(
    page.getByRole("heading", { name: "E2E One Shot" }),
  ).toBeVisible();
  // Manual metadata locks its fields.
  await expect(
    page.getByRole("button", { name: "Unlock Title" }),
  ).toBeVisible();
  await expect(
    page.getByRole("cell", { name: "5", exact: true }),
  ).toBeVisible();
});
