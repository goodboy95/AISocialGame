import { test, expect } from "@playwright/test";

/**
 * 移动端响应式回归：390x844 下 header 不产生横向溢出。
 * 右侧「好友 / 通知 / 语言选择器」收纳于「更多」菜单，
 * 余额、登录与语言切换（三语）仍可达且清晰。
 */
test.use({ viewport: { width: 390, height: 844 } });

async function assertNoHorizontalOverflow(page: import("@playwright/test").Page) {
  const metrics = await page.evaluate(() => {
    const doc = document.documentElement;
    const header = document.querySelector("header");
    return {
      docScrollWidth: doc.scrollWidth,
      docClientWidth: doc.clientWidth,
      headerScrollWidth: header ? header.scrollWidth : 0,
      headerClientWidth: header ? header.clientWidth : 0,
    };
  });
  expect(metrics.docScrollWidth, "document 不应横向溢出").toBeLessThanOrEqual(metrics.docClientWidth);
  expect(metrics.headerScrollWidth, "header 不应横向溢出").toBeLessThanOrEqual(metrics.headerClientWidth);
}

test("三语下 390px header 无横向溢出且关键操作可达", async ({ page }) => {
  await page.route("**/api/games**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([
        {
          id: "werewolf",
          name: "狼人杀",
          description: "经典社交推理",
          coverUrl: "Moon",
          status: "active",
          tags: ["推理", "社交"],
          onlineCount: 12,
        },
      ]),
    });
  });

  await page.goto("/");
  await assertNoHorizontalOverflow(page);

  // 默认简体中文：关键操作可达
  await expect(page.getByLabel("更多")).toBeVisible();
  await expect(page.getByLabel("钱包")).toBeVisible();
  await expect(page.getByText("登录")).toBeVisible();
  // 快速开始在移动端底部导航（速配）
  await expect(page.getByText("速配")).toBeVisible();

  // 「更多」菜单收纳好友/通知/语言
  await page.getByLabel("更多").click();
  await expect(page.getByText("选择语言")).toBeVisible();
  await expect(page.getByText("好友", { exact: true })).toBeVisible();

  // 切到 English 后 header 仍无溢出
  await page.getByLabel("English").click();
  await assertNoHorizontalOverflow(page);
  await expect(page.getByLabel("More")).toBeVisible();
  await expect(page.getByLabel("Wallet")).toBeVisible();
  await expect(page.getByRole("button", { name: /Log in/i })).toBeVisible();

  // 切到繁體中文后仍无溢出
  await page.getByLabel("More").click();
  await page.getByLabel("繁體中文").click();
  await assertNoHorizontalOverflow(page);
  await expect(page.getByLabel("更多")).toBeVisible();
  await expect(page.getByText("登錄")).toBeVisible();

  // 键盘可达：focus「更多」按钮 → Enter 打开 → 语言项可见（不依赖指针）
  await page.getByLabel("更多").focus();
  await page.keyboard.press("Enter");
  await expect(page.getByLabel("简体中文")).toBeVisible();
  // 键盘选择语言
  await page.getByLabel("简体中文").press("Enter");
  await assertNoHorizontalOverflow(page);
  await expect(page.getByText("登录")).toBeVisible();
});
