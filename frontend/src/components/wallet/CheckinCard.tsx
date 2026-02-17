import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { CheckinStatusResponse } from "@/types";

interface Props {
  status: CheckinStatusResponse | null;
  checking: boolean;
  onCheckin: () => void;
}

const CheckinCard = ({ status, checking, onCheckin }: Props) => {
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-lg">每日签到</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col md:flex-row md:items-center md:justify-between gap-3">
        <div className="text-sm text-muted-foreground">
          {status?.checkedInToday ? "今日已签到" : "今日未签到"}
          {status?.checkedInToday && status.tokensGrantedToday > 0 ? `，已获得 ${status.tokensGrantedToday} 积分` : ""}
        </div>
        <Button disabled={checking || !!status?.checkedInToday} onClick={onCheckin}>
          {status?.checkedInToday ? "今日已签到" : "签到领积分"}
        </Button>
      </CardContent>
    </Card>
  );
};

export default CheckinCard;
