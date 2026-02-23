import { expect, test } from "@playwright/test";

test("v2 导航入口与社交面板可用", async ({ page }) => {
  await page.goto("/achievements");
  await expect(page.getByRole("heading", { name: "成就中心" })).toBeVisible();

  await page.goto("/replays");
  await expect(page.getByRole("heading", { name: "对局回放" })).toBeVisible();

  await page.goto("/guide");
  await expect(page.getByRole("heading", { name: "新手引导与规则百科" })).toBeVisible();

  await page.goto("/");
  await page.getByRole("button", { name: /快速开始|速配/ }).first().click({ force: true });
  await expect(page.getByRole("dialog", { name: "快速匹配" })).toBeVisible();
  await page.getByRole("button", { name: "取消" }).click({ force: true });

  await page.locator("button:has(svg.lucide-users)").first().click({ force: true });
  await expect(page.getByText("好友请求")).toBeVisible();
});
