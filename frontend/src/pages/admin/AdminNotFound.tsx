import { useEffect } from "react";
import { reportClientError } from "@/lib/client-error-reporting";

/**
 * Admin 入口专属 404：维持原简中流程，不引入任何 i18n 代码，
 * 由 App.tsx（admin）使用；用户端 404 见 pages/NotFound.tsx（随语言渲染）。
 */
const AdminNotFound = () => {
  useEffect(() => {
    reportClientError(new Error("Route not found"), "router.not-found");
  }, []);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100">
      <div className="text-center">
        <h1 className="text-4xl font-bold mb-4">404</h1>
        <p className="text-xl text-gray-600 mb-4">页面不存在</p>
        <a href="/" className="text-blue-500 hover:text-blue-700 underline">
          返回首页
        </a>
      </div>
    </div>
  );
};

export default AdminNotFound;
