import i18n from "./config";

/**
 * 后端错误消息本地化：
 * 后端返回的 raw 中文错误不得直接展示给繁中/英文用户。
 * 1) 命中已知中文模式 → 映射到 errors.* 本地化通用文案（任意语言一致）；
 * 2) 未命中时：zh-CN 保留原始后端消息（本身就是中文），zh-TW/en 使用调用方兜底 key。
 */
const RAW_PATTERN_KEYS: Array<[RegExp, string]> = [
  [/房间已满|人满/, "errors.roomFull"],
  [/未通过安全|安全检查|违规/, "errors.contentBlocked"],
  [/请先登录|未登录/, "errors.loginRequired"],
  [/不存在|未找到/, "errors.notFound"],
  [/已出局/, "errors.eliminated"],
  [/已完成投票|已投过票/, "errors.alreadyVoted"],
  [/不需要你发言|未轮到你|还没轮到你/, "errors.notYourTurn"],
  [/当前阶段不支持|阶段不支持|该阶段不允许/, "errors.phaseNotSupported"],
  [/余额不足|积分不足/, "errors.insufficientBalance"],
  [/无权|权限不足|禁止访问/, "errors.forbidden"],
  [/已结束|已结算/, "errors.gameEnded"],
  [/流式请求失败|请求失败/, "aiChat.failed"],
];

export function localizeErrorMessage(raw: string | undefined, fallbackKey: string): string {
  if (raw) {
    for (const [pattern, key] of RAW_PATTERN_KEYS) {
      if (pattern.test(raw)) {
        return i18n.t(key);
      }
    }
  }
  if (!raw || i18n.language === "zh-CN") {
    // zh-CN 下原始后端消息即为用户语言，直接透出
    return raw || i18n.t(fallbackKey);
  }
  return i18n.t(fallbackKey);
}
