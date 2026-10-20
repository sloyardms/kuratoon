import { expect, test, type Page } from "@playwright/test";

/** Roadmap phase 2: first-run setup → login → logout. */

const ADMIN = {
  username: "owner",
  email: "owner@example.test",
  password: "e2e-owner-password",
};

async function completeSetupIfRequired(page: Page) {
  await page.goto("/");
  // On a fresh database every route leads to /setup; on a retry setup is already done.
  await page.waitForURL(/\/(setup|login)/);
  if (!page.url().includes("/setup")) return;

  await expect(
    page.getByRole("heading", { name: "Welcome to Kuratoon" }),
  ).toBeVisible();
  await page.getByLabel("Username").fill(ADMIN.username);
  await page.getByLabel("Email (optional)").fill(ADMIN.email);
  await page.getByLabel("Password", { exact: true }).fill(ADMIN.password);
  await page.getByLabel("Confirm password").fill(ADMIN.password);
  await page.getByRole("button", { name: "Create admin" }).click();
  await page.waitForURL(/\/login$/);
}

test("first-run setup, login and logout", async ({ page, context }) => {
  await completeSetupIfRequired(page);

  // Setup is closed once an admin exists.
  await page.goto("/setup");
  await expect(page).toHaveURL(/\/login$/);

  // A wrong password shows the generic error.
  await page.getByLabel("Username").fill(ADMIN.username);
  await page.getByLabel("Password").fill("not-the-password");
  await page.getByRole("button", { name: "Log in" }).click();
  // (Next.js also renders an empty role="alert" route announcer, so match the text.)
  await expect(
    page
      .getByRole("alert")
      .filter({ hasText: "Invalid username or password." }),
  ).toBeVisible();

  await page.getByLabel("Password").fill(ADMIN.password);
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(
    page.getByRole("heading", { name: "Your library" }),
  ).toBeVisible();

  // Both auth cookies are httpOnly (never readable by page scripts).
  const cookies = await context.cookies();
  for (const name of ["kuratoon_access", "kuratoon_refresh"]) {
    const cookie = cookies.find((c) => c.name === name);
    expect(cookie, name).toBeDefined();
    expect(cookie!.httpOnly).toBe(true);
    expect(cookie!.sameSite).toBe("Lax");
  }
  expect(await page.evaluate(() => document.cookie)).not.toContain("kuratoon_");

  // Admins see the admin menu and can open the Users page.
  await page.getByRole("button", { name: "Admin" }).click();
  await page.getByRole("menuitem", { name: "Users" }).click();
  await expect(page.getByRole("heading", { name: "Users" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "owner (you)" })).toBeVisible();

  // Log out: cookies are cleared and protected pages send you back to login.
  await page.getByRole("button", { name: "User menu" }).click();
  await page.getByRole("menuitem", { name: "Log out", exact: true }).click();
  await page.waitForURL(/\/login$/);
  expect(
    (await context.cookies()).filter((c) => c.name.startsWith("kuratoon_")),
  ).toEqual([]);

  await page.goto("/admin/users");
  await expect(page).toHaveURL(/\/login\?next=%2Fadmin%2Fusers$/);
});
