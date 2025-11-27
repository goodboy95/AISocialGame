import { test, expect } from "@playwright/test";

test("首页展示热门游戏卡片并可进入房间列表", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByText("热门游戏")).toBeVisible();
  const enterButton = page.getByRole("button", { name: /进入大厅/ }).first();
  await expect(enterButton).toBeVisible();
  await enterButton.click();
  await page.waitForLoadState("networkidle");
  console.log("current url", page.url());
  await expect(page).toHaveURL(/game/);
  await page.waitForTimeout(1000);
});
