import { Button } from "@/components/ui/button";
import { Loader2, WifiOff } from "lucide-react";

interface ConnectionStatusBarProps {
  connected: boolean;
  showReconnectAction: boolean;
  onReconnect: () => void;
}

export const ConnectionStatusBar = ({ connected, showReconnectAction, onReconnect }: ConnectionStatusBarProps) => {
  if (connected) {
    return null;
  }

  return (
    <div className="fixed left-0 right-0 top-0 z-[80] flex items-center justify-center gap-3 bg-amber-500 px-4 py-2 text-sm text-white">
      {showReconnectAction ? (
        <>
          <WifiOff className="h-4 w-4" />
          <span>连接已断开</span>
          <Button size="sm" variant="secondary" className="h-6 text-xs" onClick={onReconnect}>
            立即重连
          </Button>
        </>
      ) : (
        <>
          <Loader2 className="h-4 w-4 animate-spin" />
          <span>连接中断，正在自动重连...</span>
        </>
      )}
    </div>
  );
};
