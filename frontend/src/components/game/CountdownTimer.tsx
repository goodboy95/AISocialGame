import { useEffect, useMemo, useState } from "react";
import { Timer } from "lucide-react";

interface CountdownTimerProps {
  phaseEndsAt?: string;
  className?: string;
}

export const CountdownTimer = ({ phaseEndsAt, className }: CountdownTimerProps) => {
  const [timeLeft, setTimeLeft] = useState(0);

  useEffect(() => {
    if (!phaseEndsAt) {
      setTimeLeft(0);
      return;
    }

    const update = () => {
      const diff = Math.max(0, Math.floor((new Date(phaseEndsAt).getTime() - Date.now()) / 1000));
      setTimeLeft(diff);
    };

    update();
    const timer = window.setInterval(update, 1000);
    return () => clearInterval(timer);
  }, [phaseEndsAt]);

  const style = useMemo(() => {
    if (timeLeft <= 5) {
      return { color: "text-red-600", bg: "bg-red-50 border-red-200", pulse: "animate-pulse" };
    }
    if (timeLeft <= 10) {
      return { color: "text-amber-600", bg: "bg-amber-50 border-amber-200", pulse: "animate-pulse" };
    }
    return { color: "text-emerald-600", bg: "bg-emerald-50 border-emerald-200", pulse: "" };
  }, [timeLeft]);

  if (timeLeft <= 0) {
    return null;
  }

  return (
    <div className={`inline-flex items-center gap-1 rounded-full border px-3 py-1 text-sm font-mono ${style.color} ${style.bg} ${style.pulse} ${className || ""}`}>
      <Timer className="h-4 w-4" />
      <span>{timeLeft}s</span>
    </div>
  );
};
