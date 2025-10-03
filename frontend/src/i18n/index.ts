import { createI18n } from "vue-i18n";
import { messages } from "./messages";

const fallback = "zh";
const hasWindow = typeof window !== "undefined";
const hasNavigator = typeof navigator !== "undefined";
const stored = hasWindow ? window.localStorage.getItem("ai-social-game-locale") : null;
const browserLocale = hasNavigator && navigator.language.toLowerCase().startsWith("zh") ? "zh" : "en";
const initialLocale = stored && stored in messages ? stored : browserLocale;

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: initialLocale,
  fallbackLocale: fallback,
  messages,
});

export function setLocale(locale: string) {
  if (!(locale in messages)) {
    return;
  }
  i18n.global.locale.value = locale;
  if (hasWindow) {
    window.localStorage.setItem("ai-social-game-locale", locale);
  }
}

export type SupportedLocale = keyof typeof messages;
