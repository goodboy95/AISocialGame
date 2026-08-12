import { useEffect, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { CheckSquare, Moon, Play, Sun } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { SettlementPanel } from "@/components/game/SettlementPanel";
import { AiSeatControl } from "./shared/AiSeatControl";
import { GameLogPanel } from "./shared/GameLogPanel";
import { GameRoomFrame } from "./shared/GameRoomFrame";
import { PlayerGrid } from "./shared/PlayerGrid";
import { useRoomRuntime } from "./shared/useRoomRuntime";

interface NightActionPayload {
  action: string;
  targetPlayerId?: string;
  useHeal?: boolean;
}

const WerewolfRoom = () => {
  const { t } = useTranslation();
  const runtime = useRoomRuntime({ defaultGameId: "werewolf", recoverableMessages: ["你已出局，无法行动"] });
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
  const [nightTarget, setNightTarget] = useState<string | null>(null);

  useEffect(() => {
    if (phase !== "DAY_VOTE") {
      setSelectedVote(null);
    }
  }, [phase]);

  const speakMutation = useMutation({
    mutationFn: () => actionMutation.mutateAsync({ type: "SPEAK", content: speakContent || "结束发言" }),
    onSuccess: () => {
      setSpeakContent("");
      invalidateRuntime();
    },
    onError: (error: unknown) => handleActionError(error, "game.speakFailed"),
  });

  const voteMutation = useMutation({
    mutationFn: () => actionMutation.mutateAsync({ type: "VOTE", targetPlayerId: selectedVote || "", abstain: false }),
    onSuccess: invalidateRuntime,
    onError: (error: unknown) => handleActionError(error, "game.voteFailed"),
  });

  const nightMutation = useMutation({
    mutationFn: (payload: NightActionPayload) =>
      actionMutation.mutateAsync({ type: "NIGHT_ACTION", nightAction: payload.action, targetPlayerId: payload.targetPlayerId, useHeal: payload.useHeal }),
    onSuccess: invalidateRuntime,
    onError: (error: unknown) => handleActionError(error, "game.nightFailed"),
  });

  const myRole = state?.myRole;
  const pending = state?.pendingAction;
  const canSpeak = phase === "DAY_DISCUSS" && state?.mySeatNumber === state?.currentSeat;
  const hasVoted = !!(state?.myPlayerId && state?.votes?.[state.myPlayerId]);
  const phaseText = [
    t("game.phaseText.prefix", { phase }),
    currentSpeaker ? t("game.phaseText.speaker", { name: currentSpeaker.displayName }) : "",
    state?.round ? t("game.phaseText.day", { round: state.round }) : "",
  ]
    .filter(Boolean)
    .join(" • ");
  const selectableNightPlayers = alivePlayers.filter((p) => p.playerId !== state?.myPlayerId);

  return (
    <GameRoomFrame
      connected={socket.connected}
      showReconnectAction={socket.showReconnectAction}
      onReconnect={socket.reconnect}
      gameId={gameId}
      phase={phase}
      showTransition={showTransition}
      tutorialId={`room-${gameId}`}
      tutorialSteps={["game.tutorial.werewolf.0", "game.tutorial.werewolf.1", "game.tutorial.werewolf.2"].map((key) => t(key))}
      title={room?.name || t("game.werewolfTitle")}
      phaseText={phaseText}
      phaseEndsAt={state?.phaseEndsAt}
      aliveCount={alivePlayers.length}
      playerCount={players.length}
      headerExtra={phase === "NIGHT" ? <Moon className="h-4 w-4 text-blue-500" /> : <Sun className="h-4 w-4 text-amber-500" />}
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
          votingPhase="DAY_VOTE"
          speakingPhase="DAY_DISCUSS"
          onSelectPlayer={setSelectedVote}
        />

        <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-3">
          <Card className="p-3">
            <div className="mb-1 text-xs text-muted-foreground">{t("game.myRole")}</div>
            <div className="text-lg font-bold">{myRole || t("game.notDealt")}</div>
            <div className="mt-1 text-xs text-muted-foreground">{t("game.rolePrivate")}</div>
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
            {phase === "NIGHT" && pending && (
              <div className="space-y-2">
                <div className="text-sm text-muted-foreground">{pending.description}</div>
                {pending.type === "WITCH" ? (
                  <div className="space-y-2">
                    <div className="flex gap-2">
                      <Button variant="secondary" className="flex-1" onClick={() => nightMutation.mutate({ action: "WITCH_SAVE", useHeal: true })}>
                        {t("game.heal")}
                      </Button>
                      <Button variant="outline" className="flex-1" onClick={() => nightMutation.mutate({ action: "WITCH_SAVE", useHeal: false })}>
                        {t("game.giveUpHeal")}
                      </Button>
                    </div>
                    <Select value={nightTarget || undefined} onValueChange={setNightTarget}>
                      <SelectTrigger>
                        <SelectValue placeholder={t("game.poisonTarget")} />
                      </SelectTrigger>
                      <SelectContent>
                        {selectableNightPlayers.map((p) => (
                          <SelectItem key={p.playerId} value={p.playerId}>
                            {p.displayName}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <Button data-testid="game-night-poison-btn" disabled={!nightTarget} onClick={() => nightTarget && nightMutation.mutate({ action: "WITCH_POISON", targetPlayerId: nightTarget })}>
                      {t("game.poison")}
                    </Button>
                  </div>
                ) : (
                  <>
                    <Select value={nightTarget || undefined} onValueChange={setNightTarget}>
                      <SelectTrigger>
                        <SelectValue placeholder={t("game.selectTarget")} />
                      </SelectTrigger>
                      <SelectContent>
                        {selectableNightPlayers.map((p) => (
                          <SelectItem key={p.playerId} value={p.playerId}>
                            {p.displayName}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <Button data-testid="game-night-submit-btn" disabled={!nightTarget} className="w-full" onClick={() => nightTarget && nightMutation.mutate({ action: pending.type, targetPlayerId: nightTarget })}>
                      {t("game.submitNight")}
                    </Button>
                  </>
                )}
              </div>
            )}
            {canSpeak && (
              <div className="space-y-2">
                <Input data-testid="game-speak-input" value={speakContent} onChange={(e) => setSpeakContent(e.target.value)} placeholder={t("game.speakPlaceholder")} />
                <Button data-testid="game-speak-submit-btn" className="w-full" onClick={() => speakMutation.mutate()} disabled={speakMutation.isPending}>
                  {t("game.endSpeak")}
                </Button>
              </div>
            )}
            {phase === "DAY_VOTE" && (
              <div className="space-y-2">
                <div className="text-xs text-muted-foreground">{t("game.voteHint")}</div>
                <Button data-testid="game-vote-submit-btn" className="w-full" disabled={!selectedVote || hasVoted || voteMutation.isPending} onClick={() => voteMutation.mutate()}>
                  <CheckSquare className="mr-2 h-4 w-4" /> {t("game.vote")}
                </Button>
              </div>
            )}
            {phase === "SETTLEMENT" && state && <SettlementPanel gameId={gameId} state={state} userKey={userKey} />}
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

      <GameLogPanel logs={state?.logs} emptyText={t("game.logEmpty.werewolf")} />
    </GameRoomFrame>
  );
};

export default WerewolfRoom;
