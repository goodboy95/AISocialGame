import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import { resources } from "./resources";

/** 持久化键：带版本号，避免与历史 localStorage 冲突 */
export const STORAGE_KEY = "aienie.user.locale.v1";

/** 支持的语言（顺序即 LanguageSelector 展示顺序） */
export const SUPPORTED_LOCALES = ["zh-CN", "zh-TW", "en"] as const;

export type Locale = (typeof SUPPORTED_LOCALES)[number];

/** 语言在界面上的原生名称 */
export const LOCALE_LABELS: Record<Locale, string> = {
  "zh-CN": "简体中文",
  "zh-TW": "繁體中文",
  en: "English",
};

export function isLocale(value: unknown): value is Locale {
  return typeof value === "string" && (SUPPORTED_LOCALES as readonly string[]).includes(value);
}

/**
 * 读取初始语言：
 * 1) localStorage 中的合法值；2) 否则回退 zh-CN。
 * 不做语言探测、不读取 Accept-Language。
 */
export function getInitialLocale(): Locale {
  if (typeof window === "undefined") return "zh-CN";
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (stored && isLocale(stored)) return stored;
  } catch {
    /* localStorage 不可用时静默回退 */
  }
  return "zh-CN";
}

/** 同步 html.lang 与 document.title（切换语言时调用） */
export function applyLocaleMeta(locale: Locale): void {
  if (typeof document === "undefined") return;
  document.documentElement.lang = locale;
  document.title = i18n.t("app.title", { lng: locale });
}

/** 切换语言并持久化 */
export function setLocale(locale: Locale): void {
  void i18n.changeLanguage(locale);
  try {
    window.localStorage.setItem(STORAGE_KEY, locale);
  } catch {
    /* ignore */
  }
  applyLocaleMeta(locale);
}

const initialLocale = getInitialLocale();

// 仅使用内存内联资源：不引入 http backend、不引入 languageDetector，
// 不读取 Accept-Language，网络不可用时也能完整工作。
i18n.use(initReactI18next).init({
  resources,
  lng: initialLocale,
  fallbackLng: "zh-CN",
  supportedLngs: [...SUPPORTED_LOCALES],
  load: "currentOnly",
  interpolation: {
    escapeValue: false, // React 默认已做 XSS 转义
  },
  react: {
    useSuspense: false, // 资源内联，无需 Suspense 等待
  },
});

applyLocaleMeta(initialLocale);

export default i18n;
