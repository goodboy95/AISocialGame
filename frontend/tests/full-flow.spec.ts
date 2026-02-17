import { test, expect } from "@playwright/test";

test("注册登录、开房游玩及社区发帖的完整流程", async ({ page }) => {
  const suffix = Date.now();
  const email = `tester${suffix}@example.com`;
  const username = `tester_${suffix}`;
  const password = "Password!23";
  const nickname = `玩家${suffix.toString().slice(-4)}`;
  const postContent = `自动化发帖 ${suffix}`;

  // 注册并自动登录
  await page.goto("/register");
  await page.getByLabel("登录用户名").fill(username);
  await page.getByLabel("游戏昵称").fill(nickname);
  await page.getByLabel("电子邮箱").fill(email);
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByLabel("确认密码", { exact: true }).fill(password);
  await page.getByRole("checkbox", { name: /同意/ }).click();
  await page.getByRole("button", { name: "创建账号" }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem("aisocialgame_token")))
    .not.toBeNull();

  // 创建谁是卧底房间并开局
  const enterButtons = page.getByRole("button", { name: "进入大厅" });
  await enterButtons.nth(1).click();
  await expect(page).toHaveURL(/game\/undercover/);
  await page.getByRole("button", { name: "创建房间" }).click();
  await page.getByRole("button", { name: "创建并入座" }).click();
  await expect(page).toHaveURL(/room\/undercover/);
  await expect(page.getByText(nickname, { exact: true }).first()).toBeVisible();

  const addUndercoverBtn = page.getByText("添加 AI 玩家").locator("..").getByRole("button", { name: "添加" });
  for (let i = 0; i < 3; i++) {
    await addUndercoverBtn.click();
    await page.waitForTimeout(300);
  }
  await page.getByRole("button", { name: /开始游戏/ }).click();
  await expect(page.getByRole("button", { name: /开始游戏/ }).first()).toBeHidden({ timeout: 8000 });

  // 创建狼人杀房间并开局
  await page.goto("/");
  await page.getByRole("button", { name: "进入大厅" }).first().click();
  await expect(page).toHaveURL(/game\/werewolf/);
  await page.getByRole("button", { name: "创建房间" }).click();
  await page.getByRole("button", { name: "创建并入座" }).click();
  await expect(page).toHaveURL(/room\/werewolf/);
  await expect(page.getByText(nickname, { exact: true }).first()).toBeVisible();

  const addWerewolfBtn = page.getByText("添加 AI 玩家").locator("..").getByRole("button", { name: "添加" });
  for (let i = 0; i < 5; i++) {
    await addWerewolfBtn.click();
    await page.waitForTimeout(200);
  }
  await page.getByRole("button", { name: /^开始游戏$/ }).first().click();
  await expect(page.getByText("天黑请闭眼").first()).toBeVisible({ timeout: 8000 });

  // 社区发帖
  await page.goto("/community");
  await page.getByPlaceholder("分享你的游戏趣事...").fill(postContent);
  await page.getByRole("button", { name: "发布" }).click();
  await expect(page.getByText(postContent)).toBeVisible();
});
