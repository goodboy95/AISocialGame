import { useEffect, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { CheckSquare, Play, Send } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { SettlementPanel } from "@/components/game/SettlementPanel";
import { AiSeatControl } from "./shared/AiSeatControl";
import { GameLogPanel } from "./shared/GameLogPanel";
import { GameRoomFrame } from "./shared/GameRoomFrame";
import { PlayerGrid } from "./shared/PlayerGrid";
import { useRoomRuntime } from "./shared/useRoomRuntime";

const UndercoverRoom = () => {
  const { t } = useTranslation();
  const runtime = useRoomRuntime({ defaultGameId: "undercover" });
  const {
    gameId,
    room,
    state,
    players,
    alivePlayers,
    currentSpeaker,
    phase,
    personas,
    selectedAiId,
    setSelectedAiId,
    canAddAi,
    chatMessages,
    socket,
    showTransition,
    userKey,
    startMutation,
    actionMutation,
    addAiMutation,
    startGame,
    handleActionError,
    invalidateRuntime,
  } = runtime;
  const [speakContent, setSpeakContent] = useState("");
  const [selectedVote, setSelectedVote] = useState<string | null>(null);

  useEffect(() => {
    if (phase !== "VOTING") {
      setSelectedVote(null);
    }
  }, [phase]);

  const speakMutation = useMutation({
    mutationFn: () => actionMutation.mutateAsync({ type: "SPEAK", content: speakContent || "我已描述完毕" }),
    onSuccess: () => {
      setSpeakContent("");
      invalidateRuntime();
    },
    onError: (error: unknown) => handleActionError(error, "game.submitSpeakFailed"),
  });

  const voteMutation = useMutation({
    mutationFn: () => actionMutation.mutateAsync({ type: "VOTE", targetPlayerId: selectedVote || "", abstain: false }),
    onSuccess: invalidateRuntime,
    onError: (error: unknown) => handleActionError(error, "game.voteFailed"),
  });

  const canSpeak = phase === "DESCRIPTION" && state?.mySeatNumber === state?.currentSeat;
  const hasVoted = !!(state?.myPlayerId && state?.votes?.[state.myPlayerId]);
  const canVote = phase === "VOTING" && !!selectedVote && !hasVoted;
  const phaseText = [
    t("game.phaseText.prefix", { phase }),
    currentSpeaker ? t("game.phaseText.speaker", { name: currentSpeaker.displayName }) : "",
    state?.round ? t("game.phaseText.round", { round: state.round }) : "",
  ]
    .filter(Boolean)
    .join(" • ");

  return (
    <GameRoomFrame
      connected={socket.connected}
      showReconnectAction={socket.showReconnectAction}
      onReconnect={socket.reconnect}
      gameId={gameId}
      phase={phase}
      showTransition={showTransition}
      tutorialId={`room-${gameId}`}
      tutorialSteps={["game.tutorial.undercover.0", "game.tutorial.undercover.1", "game.tutorial.undercover.2"].map((key) => t(key))}
      title={room?.name || t("game.undercoverTitle")}
      phaseText={phaseText}
      phaseEndsAt={state?.phaseEndsAt}
      aliveCount={alivePlayers.length}
      playerCount={players.length}
      chatMessages={chatMessages}
      myPlayerId={state?.myPlayerId}
      onSendChat={(type, content) => {
        const sent = socket.sendChat(type, content);
        if (!sent) {
          toast.error(t("game.chat.sendFailed"));
        }
      }}
    >
      <Card className="p-4">
        <PlayerGrid
          players={players}
          myPlayerId={state?.myPlayerId}
          selectedPlayerId={selectedVote}
          currentSeat={state?.currentSeat}
          phase={phase}
          votingPhase="VOTING"
          speakingPhase="DESCRIPTION"
          onSelectPlayer={setSelectedVote}
        />

        <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-3">
          <Card className="border-dashed p-3">
            <div className="mb-2 text-xs text-muted-foreground">{t("game.myWord")}</div>
            <div className="text-lg font-bold">{state?.myWord || t("game.waitingDeal")}</div>
            <div className="mt-1 text-xs text-muted-foreground">{state?.myRole === "UNDERCOVER" ? t("game.wordUndercover") : t("game.wordHint")}</div>
          </Card>

          <Card className="p-3">
            <div className="mb-2 flex items-center justify-between">
              <span className="text-sm font-medium">{t("game.operation")}</span>
              <Badge variant="outline">{phase}</Badge>
            </div>
            {phase === "WAITING" && (
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground">{t("game.waitStart")}</p>
                <Button data-testid="game-start-btn" onClick={startGame} disabled={startMutation.isPending} className="w-full">
                  <Play className="mr-2 h-4 w-4" /> {t("lobby.startGame")}
                </Button>
              </div>
            )}
            {canSpeak && (
              <div className="space-y-2">
                <Input data-testid="game-speak-input" value={speakContent} onChange={(e) => setSpeakContent(e.target.value)} placeholder={t("game.descriptionPlaceholder")} />
                <Button data-testid="game-speak-submit-btn" className="w-full" onClick={() => speakMutation.mutate()} disabled={speakMutation.isPending}>
                  <Send className="mr-2 h-4 w-4" /> {t("game.submitSpeak")}
                </Button>
              </div>
            )}
            {phase === "VOTING" && (
              <div className="space-y-2">
                <div className="text-xs text-muted-foreground">{t("game.voteHint")}</div>
                <Button data-testid="game-vote-submit-btn" className="w-full" disabled={!canVote || voteMutation.isPending} onClick={() => voteMutation.mutate()}>
                  <CheckSquare className="mr-2 h-4 w-4" /> {t("game.vote")}
                </Button>
              </div>
            )}
            {phase === "SETTLEMENT" && state && <SettlementPanel gameId={gameId} state={state} userKey={userKey} />}
            {!canSpeak && phase === "DESCRIPTION" && (
              <div className="text-sm text-muted-foreground">{t("game.waitingSpeaker", { name: currentSpeaker?.displayName || t("game.player") })}</div>
            )}
          </Card>

          <AiSeatControl
            personas={personas}
            selectedAiId={selectedAiId}
            onSelectedAiIdChange={setSelectedAiId}
            seatCount={room?.seats?.length ?? 0}
            maxPlayers={room?.maxPlayers}
            canAddAi={canAddAi}
            onAddAi={() => addAiMutation.mutate(selectedAiId)}
          />
        </div>
      </Card>

      <GameLogPanel logs={state?.logs} emptyText={t("game.logEmpty.undercover")} />
    </GameRoomFrame>
  );
};

export default UndercoverRoom;
