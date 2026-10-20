import { expect, test } from "@playwright/test";

test("home page loads", async ({ page }) => {
  await page.goto("/");

  await expect(page).toHaveTitle("Kuratoon");
  await expect(
    page.getByRole("heading", { level: 1, name: "Kuratoon" }),
  ).toBeVisible();
  await expect(page.locator("html")).toHaveClass(/\bdark\b/);
});
