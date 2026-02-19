import { expect, test } from "@playwright/test";

test("v2 导航入口与社交面板可用", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("link", { name: "成就" }).click();
  await expect(page.getByRole("heading", { name: "成就中心" })).toBeVisible();

  await page.getByRole("link", { name: "回放" }).click();
  await expect(page.getByRole("heading", { name: "对局回放" })).toBeVisible();

  await page.getByRole("link", { name: "百科" }).click();
  await expect(page.getByRole("heading", { name: "新手引导与规则百科" })).toBeVisible();

  await page.getByRole("button", { name: "快速开始" }).first().click();
  await expect(page.getByRole("dialog", { name: "快速匹配" })).toBeVisible();
  await page.getByRole("button", { name: "取消" }).click();

  await page.getByRole("button").nth(1).click();
  await expect(page.getByRole("dialog", { name: "好友" })).toBeVisible();
});
