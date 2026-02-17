import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { UsageRecord } from "@/types";

interface Props {
  title: string;
  records: UsageRecord[];
  page: number;
  hasMore: boolean;
  loading: boolean;
  onPrev: () => void;
  onNext: () => void;
}

const UsageRecordList = ({ title, records, page, hasMore, loading, onPrev, onNext }: Props) => {
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-lg">{title}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {!records.length && <div className="text-sm text-muted-foreground">暂无记录</div>}
        {records.map((item) => (
          <div key={`${item.requestId}-${item.createdAt}`} className="rounded-lg border p-3 text-sm">
            <div className="flex items-center justify-between gap-3">
              <div className="font-medium">{item.modelKey || "未知模型"}</div>
              <div className="text-red-600">-{item.billedTokens} tokens</div>
            </div>
            <div className="mt-1 text-xs text-muted-foreground">
              prompt {item.promptTokens} / completion {item.completionTokens}
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

export default UsageRecordList;
