import { Game } from "@/types";

/**
 * 用户可见游戏展示文案（name/description/tags）的本地化映射：
 * 以稳定 game.id 为枚举，映射到 src/i18n/resources.ts 的 t key。
 * UI 渲染游戏卡片/房间列表时优先取 GAME_DISPLAY_KEYS 的翻译，
 * 未知游戏 id 则回退到后端/本地原始数据（见 src/i18n/gameTexts.ts）。
 * GAMES 中保留的 name/description/tags 为 zh-CN 兜底文案（与映射同义）。
 */
export const GAME_DISPLAY_KEYS: Record<string, { name: string; desc: string; tags: string[] }> = {
  werewolf: {
    name: "games.werewolf.name",
    desc: "games.werewolf.desc",
    tags: ["games.werewolf.tags.0", "games.werewolf.tags.1", "games.werewolf.tags.2"],
  },
  undercover: {
    name: "games.undercover.name",
    desc: "games.undercover.desc",
    tags: ["games.undercover.tags.0", "games.undercover.tags.1", "games.undercover.tags.2"],
  },
  turtle_soup: {
    name: "games.turtle_soup.name",
    desc: "games.turtle_soup.desc",
    tags: ["games.turtle_soup.tags.0", "games.turtle_soup.tags.1", "games.turtle_soup.tags.2"],
  },
};

/**
 * configSchema 字段/选项展示文案的本地化映射（CreateRoom 板子配置区域）：
 * 以稳定 game.id + field.id → t key；选项以 option.value 字符串化后映射。
 * 未知 id 回退到 GAMES 中保留的 zh-CN label（见 src/i18n/gameTexts.ts）。
 */
type ConfigFieldKeys = Record<string, { label: string; options?: Record<string, string> }>;

export const GAME_CONFIG_KEYS: Record<string, ConfigFieldKeys> = {
  werewolf: {
    template: {
      label: "create.field.template",
      options: {
        standard: "create.option.template.standard",
        guard: "create.option.template.guard",
        no_god: "create.option.template.no_god",
      },
    },
    playerCount: {
      label: "create.field.playerCount",
      options: {
        "1": "create.option.playerCount.1",
        "2": "create.option.playerCount.2",
        "3": "create.option.playerCount.3",
        "4": "create.option.playerCount.4",
        "5": "create.option.playerCount.5",
        "6": "create.option.playerCount.6",
        "7": "create.option.playerCount.7",
        "8": "create.option.playerCount.8",
        "9": "create.option.playerCount.9",
        "10": "create.option.playerCount.10",
        "12": "create.option.playerCount.12",
      },
    },
    witchRule: {
      label: "create.field.witchRule",
      options: {
        no_save: "create.option.witchRule.no_save",
        first_night: "create.option.witchRule.first_night",
        always_save: "create.option.witchRule.always_save",
      },
    },
    winCondition: {
      label: "create.field.winCondition",
      options: {
        side: "create.option.winCondition.side",
        city: "create.option.winCondition.city",
      },
    },
    speechTime: {
      label: "create.field.speechTime",
      options: {
        "60": "create.option.speakTime.60",
        "90": "create.option.speakTime.90",
        "120": "create.option.speakTime.120",
      },
    },
    hasLastWords: {
      label: "create.field.hasLastWords",
      options: {
        first_night: "create.option.hasLastWords.first_night",
        always: "create.option.hasLastWords.always",
        none: "create.option.hasLastWords.none",
      },
    },
  },
  undercover: {
    playerCount: {
      label: "create.field.playerCount",
      options: {
        "4": "create.option.playerCount.4",
        "5": "create.option.playerCount.5",
        "6": "create.option.playerCount.6",
        "7": "create.option.playerCount.7",
        "8": "create.option.playerCount.8",
        "9": "create.option.playerCount.9",
        "10": "create.option.playerCount.10",
      },
    },
    spyMode: {
      label: "create.field.spyMode",
      options: {
        auto: "create.option.spyMode.auto",
        manual: "create.option.spyMode.manual",
      },
    },
    hasBlank: { label: "create.field.hasBlank" },
    wordPack: {
      label: "create.field.wordPack",
      options: {
        daily: "create.option.wordPack.daily",
        idiom: "create.option.wordPack.idiom",
        acg: "create.option.wordPack.acg",
        tech: "create.option.wordPack.tech",
        custom: "create.option.wordPack.custom",
      },
    },
    speakTime: {
      label: "create.field.speakTime",
      options: {
        "30": "create.option.speakTime.30",
        "60": "create.option.speakTime.60",
        "90": "create.option.speakTime.90",
        "0": "create.option.speakTime.0",
      },
    },
  },
  turtle_soup: {
    playerCount: {
      label: "create.field.playerCount",
      options: {
        "1": "create.option.playerCount.1",
        "2": "create.option.playerCount.2",
        "3": "create.option.playerCount.3",
        "4": "create.option.playerCount.4",
        "6": "create.option.playerCount.6",
      },
    },
    caseId: {
      label: "create.field.caseId",
      options: {
        midnight_train: "create.option.caseId.midnight_train",
        rainy_key: "create.option.caseId.rainy_key",
      },
    },
    maxQuestions: { label: "create.field.maxQuestions" },
    aiAssist: { label: "create.field.aiAssist" },
  },
};

export const GAMES: Game[] = [
  {
    id: "werewolf",
    name: "狼人杀",
    description: "经典的社交推理游戏。天黑请闭眼，与伪装者博弈，活到最后。",
    coverUrl: "Moon",
    tags: ["逻辑推理", "社交", "硬核"],
    minPlayers: 6,
    maxPlayers: 12,
    status: "active",
    onlineCount: 1240,
    configSchema: [
      // --- 基础板子配置 (Templates) ---
      {
        id: "template",
        label: "板子预设",
        type: "select", // In UI this will be rendered as cards
        defaultValue: "standard",
        options: [
          { label: "预女猎白 (标准)", value: "standard" },
          { label: "预女猎守 (进阶)", value: "guard" },
          { label: "生推局 (无神职)", value: "no_god" },
        ],
      },
      {
        id: "playerCount",
        label: "玩家人数",
        type: "select",
        defaultValue: 12,
        options: [
          { label: "6人 (娱乐)", value: 6 },
          { label: "9人 (进阶)", value: 9 },
          { label: "12人 (标准)", value: 12 },
        ],
      },
      // --- 高级规则 (Advanced) ---
      {
        id: "witchRule",
        label: "女巫规则",
        type: "select",
        defaultValue: "first_night",
        options: [
          { label: "全程不可自救", value: "no_save" },
          { label: "仅首夜可自救", value: "first_night" },
          { label: "全程可自救", value: "always_save" },
        ],
      },
      {
        id: "winCondition",
        label: "胜利条件",
        type: "select",
        defaultValue: "side",
        options: [
          { label: "屠边规则", value: "side" },
          { label: "屠城规则", value: "city" },
        ],
      },
      {
        id: "speechTime",
        label: "发言时长",
        type: "select",
        defaultValue: 120,
        options: [
          { label: "60秒", value: 60 },
          { label: "90秒", value: 90 },
          { label: "120秒", value: 120 },
        ],
      },
      {
        id: "hasLastWords",
        label: "遗言规则",
        type: "select",
        defaultValue: "first_night",
        options: [
          { label: "仅首夜", value: "first_night" },
          { label: "全程有遗言", value: "always" },
          { label: "无遗言", value: "none" },
        ],
      },
    ],
  },
  {
    id: "undercover",
    name: "谁是卧底",
    description: "用语言描述你的词语，找出隐藏在人群中的卧底！",
    coverUrl: "Spy",
    tags: ["聚会", "休闲", "语言类"],
    minPlayers: 4,
    maxPlayers: 10,
    status: "active",
    onlineCount: 856,
    configSchema: [
      {
        id: "playerCount",
        label: "玩家人数",
        type: "select",
        defaultValue: 6,
        options: [
          { label: "4人", value: 4 },
          { label: "5人", value: 5 },
          { label: "6人", value: 6 },
          { label: "7人", value: 7 },
          { label: "8人", value: 8 },
          { label: "9人", value: 9 },
          { label: "10人", value: 10 },
        ],
      },
      {
        id: "spyMode",
        label: "卧底数量模式",
        type: "select",
        defaultValue: "auto",
        options: [
          { label: "系统自动 (推荐)", value: "auto" },
          { label: "手动设置", value: "manual" },
        ],
      },
      {
        id: "hasBlank",
        label: "加入白板玩家",
        type: "boolean",
        defaultValue: false,
      },
      {
        id: "wordPack",
        label: "词库类型",
        type: "select",
        defaultValue: "daily",
        options: [
          { label: "日常生活", value: "daily" },
          { label: "成语俗语", value: "idiom" },
          { label: "二次元", value: "acg" },
          { label: "硬核科技", value: "tech" },
          { label: "自定义词库", value: "custom" },
        ],
      },
      {
        id: "speakTime",
        label: "发言时长",
        type: "select",
        defaultValue: 60,
        options: [
          { label: "30秒", value: 30 },
          { label: "60秒", value: 60 },
          { label: "90秒", value: 90 },
          { label: "不限时", value: 0 },
        ],
      },
    ],
  },
  {
    id: "turtle_soup",
    name: "海龟汤",
    description: "通过提问“是”或“否”来还原离奇故事的真相。",
    coverUrl: "BookOpen",
    tags: ["悬疑", "合作", "故事"],
    minPlayers: 1,
    maxPlayers: 6,
    status: "active",
    onlineCount: 0,
    configSchema: [
      {
        id: "playerCount",
        label: "玩家人数",
        type: "select",
        defaultValue: 2,
        options: [
          { label: "1人", value: 1 },
          { label: "2人", value: 2 },
          { label: "3人", value: 3 },
          { label: "4人", value: 4 },
          { label: "6人", value: 6 },
        ],
      },
      {
        id: "caseId",
        label: "题目",
        type: "select",
        defaultValue: "midnight_train",
        options: [
          { label: "末班车的乘客", value: "midnight_train" },
          { label: "雨夜的钥匙", value: "rainy_key" },
        ],
      },
      {
        id: "maxQuestions",
        label: "问题上限",
        type: "number",
        defaultValue: 12,
        min: 4,
        max: 30,
      },
      {
        id: "aiAssist",
        label: "AI 玩家追问",
        type: "boolean",
        defaultValue: true,
      },
    ],
  },
];
