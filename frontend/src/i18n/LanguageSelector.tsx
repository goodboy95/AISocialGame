import { Check, Languages } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { LOCALE_LABELS, SUPPORTED_LOCALES, setLocale, type Locale } from "./config";

/**
 * 语言菜单项（无 DropdownMenu 外壳）：供 header 桌面语言选择器与
 * 移动端「更多」收纳菜单复用，保证两处交互与键盘可达一致。
 */
export function LanguageMenuItems() {
  const { i18n } = useTranslation();
  const current = i18n.language as Locale;

  return (
    <>
      {SUPPORTED_LOCALES.map((locale) => (
        <DropdownMenuItem
          key={locale}
          onClick={() => setLocale(locale)}
          aria-label={LOCALE_LABELS[locale]}
          aria-checked={current === locale}
        >
          {current === locale && <Check className="mr-2 h-4 w-4" />}
          {LOCALE_LABELS[locale]}
        </DropdownMenuItem>
      ))}
    </>
  );
}

/**
 * 语言选择器：桌面端直接显示于 header；移动端收纳于「更多」菜单。
 * 使用 shadcn DropdownMenu（Radix），天然支持键盘导航；
 * 切换时更新 localStorage、html.lang 与 document.title。
 */
export function LanguageSelector() {
  const { t } = useTranslation();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="outline"
          size="icon"
          aria-label={t("i18n.selectLanguage")}
          title={t("i18n.selectLanguage")}
        >
          <Languages className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-[9rem]">
        <LanguageMenuItems />
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

export default LanguageSelector;
