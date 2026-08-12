import { useTranslation } from "react-i18next";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

interface Props {
  totalTokens: number;
  projectPermanentTokens: number;
  projectTempTokens: number;
}

const BalanceOverview = ({ totalTokens, projectPermanentTokens, projectTempTokens }: Props) => {
  const { t } = useTranslation();
  const items = [
    { label: t("wallet.totalTokens"), value: totalTokens },
    { label: t("wallet.projectPermanent"), value: projectPermanentTokens },
    { label: t("wallet.projectTemp"), value: projectTempTokens },
  ];

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-lg">{t("wallet.balanceTitle")}</CardTitle>
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
