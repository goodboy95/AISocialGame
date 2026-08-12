import { useTranslation } from "react-i18next";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { CheckinStatusResponse } from "@/types";

interface Props {
  status: CheckinStatusResponse | null;
  checking: boolean;
  onCheckin: () => void;
}

const CheckinCard = ({ status, checking, onCheckin }: Props) => {
  const { t } = useTranslation();
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-lg">{t("wallet.checkinTitle")}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col md:flex-row md:items-center md:justify-between gap-3">
        <div className="text-sm text-muted-foreground">
          {status?.checkedInToday ? t("wallet.checkinToday") : t("wallet.notCheckedIn")}
          {status?.checkedInToday && status.tokensGrantedToday > 0 ? t("wallet.checkinGained", { count: status.tokensGrantedToday }) : ""}
        </div>
        <Button disabled={checking || !!status?.checkedInToday} onClick={onCheckin}>
          {status?.checkedInToday ? t("wallet.checkinToday") : t("wallet.checkinButton")}
        </Button>
      </CardContent>
    </Card>
  );
};

export default CheckinCard;
