import { createRoot } from "react-dom/client";
import { SafeErrorBoundary } from "@/components/SafeErrorBoundary";
import { installGlobalErrorHandlers } from "@/lib/client-error-reporting";
import "./globals.css";

installGlobalErrorHandlers();

/**
 * 入口按路径精确分支：
 * - /admin 及 /admin/* → 动态加载 admin 入口（App.tsx，原简中流程，不包含 i18n）
 * - 其余用户路由 → 动态加载 UserApp.tsx（I18nextProvider 包裹）
 * 动态 import 保证两侧代码分包，admin 侧永不加载 i18n 模块。
 */
const pathname = window.location.pathname;
const isAdminPath = pathname === "/admin" || pathname.startsWith("/admin/");

async function bootstrap() {
  const root = createRoot(document.getElementById("root")!);
  if (isAdminPath) {
    const { default: App } = await import("./App");
    // admin 侧：SafeErrorBoundary 默认简中文案，不加载任何 i18n 模块
    root.render(
      <SafeErrorBoundary>
        <App />
      </SafeErrorBoundary>,
    );
  } else {
    const { default: UserApp, UserErrorBoundary } = await import("./UserApp");
    root.render(
      <UserErrorBoundary>
        <UserApp />
      </UserErrorBoundary>,
    );
  }
}

void bootstrap();
