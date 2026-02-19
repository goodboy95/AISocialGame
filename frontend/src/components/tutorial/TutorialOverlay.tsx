import { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

interface TutorialOverlayProps {
  id: string;
  steps: string[];
}

export const TutorialOverlay = ({ id, steps }: TutorialOverlayProps) => {
  const key = useMemo(() => `aisocial_tutorial_${id}`, [id]);
  const [open, setOpen] = useState(false);
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const done = localStorage.getItem(key) === "done";
    if (!done) {
      setOpen(true);
    }
  }, [key]);

  if (!open || steps.length === 0) {
    return null;
  }

  const isLast = index >= steps.length - 1;

  return (
    <div className="fixed inset-0 z-[90] flex items-end justify-center bg-black/40 p-4 md:items-center">
      <Card className="w-full max-w-lg space-y-4 p-4">
        <div className="text-xs text-muted-foreground">
          新手引导 {index + 1}/{steps.length}
        </div>
        <div className="text-sm">{steps[index]}</div>
        <div className="flex justify-end gap-2">
          <Button
            variant="outline"
            onClick={() => {
              localStorage.setItem(key, "done");
              setOpen(false);
            }}
          >
            跳过
          </Button>
          <Button
            onClick={() => {
              if (isLast) {
                localStorage.setItem(key, "done");
                setOpen(false);
              } else {
                setIndex((prev) => prev + 1);
              }
            }}
          >
            {isLast ? "完成" : "下一步"}
          </Button>
        </div>
      </Card>
    </div>
  );
};
