import { Component, type ReactNode } from "react";
import { reportClientError } from "@/lib/client-error-reporting";

type Props = {
  children: ReactNode;
  /** 用户端传入本地化文案；缺省时使用简体中文兜底（admin 侧不接入 i18n） */
  texts?: { title: string; description: string; retry: string };
};

type State = {
  failed: boolean;
};

const DEFAULT_TEXTS = {
  title: "页面暂时无法显示",
  description: "错误已捕获，请刷新后重试。",
  retry: "刷新页面",
};

export class SafeErrorBoundary extends Component<Props, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  componentDidCatch(error: Error): void {
    reportClientError(error, "react.error-boundary");
  }

  render(): ReactNode {
    if (this.state.failed) {
      const texts = this.props.texts || DEFAULT_TEXTS;
      return (
        <main className="flex min-h-screen items-center justify-center bg-slate-50 px-6 text-slate-900">
          <section className="max-w-md text-center" role="alert">
            <h1 className="text-2xl font-semibold">{texts.title}</h1>
            <p className="mt-3 text-sm text-slate-600">{texts.description}</p>
            <button
              type="button"
              className="mt-6 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
              onClick={() => window.location.reload()}
            >
              {texts.retry}
            </button>
          </section>
        </main>
      );
    }
    return this.props.children;
  }
}
