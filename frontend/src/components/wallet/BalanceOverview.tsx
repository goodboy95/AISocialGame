import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

interface Props {
  totalTokens: number;
  projectPermanentTokens: number;
  projectTempTokens: number;
}

const BalanceOverview = ({ totalTokens, projectPermanentTokens, projectTempTokens }: Props) => {
  const items = [
    { label: "总积分", value: totalTokens },
    { label: "项目永久", value: projectPermanentTokens },
    { label: "项目临时", value: projectTempTokens },
  ];

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-lg">余额概览</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          {items.map((item) => (
            <div key={item.label} className="rounded-xl border bg-slate-50 p-4">
              <div className="text-xs text-slate-500">{item.label}</div>
              <div className="mt-1 text-2xl font-semibold">{item.value}</div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
};

export default BalanceOverview;
