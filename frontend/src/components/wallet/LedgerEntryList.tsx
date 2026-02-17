import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { LedgerEntry } from "@/types";

interface Props {
  entries: LedgerEntry[];
  page: number;
  hasMore: boolean;
  loading: boolean;
  onPrev: () => void;
  onNext: () => void;
}

const LedgerEntryList = ({ entries, page, hasMore, loading, onPrev, onNext }: Props) => {
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-lg">账本明细</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {!entries.length && <div className="text-sm text-muted-foreground">暂无记录</div>}
        {entries.map((item) => (
          <div key={`${item.id}-${item.createdAt}`} className="rounded-lg border p-3 text-sm">
            <div className="flex items-center justify-between gap-3">
              <div className="font-medium">{item.type}</div>
              <div className={item.tokens >= 0 ? "text-emerald-600" : "text-red-600"}>
                {item.tokens >= 0 ? "+" : ""}
                {item.tokens}
              </div>
            </div>
            <div className="mt-1 text-xs text-muted-foreground">
              {item.reason || "-"}
              {item.createdAt ? ` · ${new Date(item.createdAt).toLocaleString()}` : ""}
            </div>
          </div>
        ))}
        <div className="flex items-center justify-end gap-2">
          <Button variant="outline" size="sm" disabled={loading || page <= 1} onClick={onPrev}>
            上一页
          </Button>
          <Button variant="outline" size="sm" disabled={loading || !hasMore} onClick={onNext}>
            下一页
          </Button>
        </div>
      </CardContent>
    </Card>
  );
};

export default LedgerEntryList;
