import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { BookOpen, HelpCircle, Lightbulb, Play, Send, Trophy } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { SettlementPanel } from "@/components/game/SettlementPanel";
import { AiSeatControl } from "./shared/AiSeatControl";
import { GameLogPanel } from "./shared/GameLogPanel";
import { GameRoomFrame } from "./shared/GameRoomFrame";
import { PlayerGrid } from "./shared/PlayerGrid";
import { useRoomRuntime } from "./shared/useRoomRuntime";

type QaItem = {
  question: string;
  answer: string;
  clues?: string[];
  aiGenerated?: boolean;
  time?: string;
};

const stringList = (value: unknown): string[] => {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
};

const qaList = (value: unknown): QaItem[] => {
  return Array.isArray(value)
    ? value
        .filter((item): item is Record<string, unknown> => item !== null && typeof item === "object")
        .map((item) => ({
          question: typeof item.question === "string" ? item.question : "",
          answer: typeof item.answer === "string" ? item.answer : "",
          clues: stringList(item.clues),
          aiGenerated: Boolean(item.aiGenerated),
          time: typeof item.time === "string" ? item.time : undefined,
        }))
    : [];
};

const TurtleSoupRoom = () => {
  const { t } = useTranslation();
  const runtime = useRoomRuntime({ defaultGameId: "turtle_soup" });
  const {
    gameId,
    room,
    state,
    players,
    alivePlayers,
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
  const [question, setQuestion] = useState("");
  const [solution, setSolution] = useState("");

  const extra = state?.extra || {};
  const knownClues = stringList(extra.knownClues);
  const qaHistory = qaList(extra.qaHistory);
  const questionCount = Number(extra.questionCount || 0);
  const maxQuestions = Number(extra.maxQuestions || 0);
  const surface = typeof extra.surface === "string" ? extra.surface : "";
  const caseTitle = typeof extra.caseTitle === "string" ? extra.caseTitle : t("games.turtle_soup.name");
  const hostVerdict = typeof extra.hostVerdict === "string" ? extra.hostVerdict : "";
  const revealedSolution = typeof extra.solution === "string" ? extra.solution : "";

  const askMutation = useMutation({
    mutationFn: () => actionMutation.mutateAsync({ type: "ASK_QUESTION", content: question.trim() }),
    onSuccess: () => {
      setQuestion("");
      invalidateRuntime();
    },
    onError: (error: unknown) => handleActionError(error, "game.askFailed"),
  });

  const solutionMutation = useMutation({
    mutationFn: () => actionMutation.mutateAsync({ type: "SUBMIT_SOLUTION", content: solution.trim() }),
    onSuccess: () => {
      invalidateRuntime();
    },
    onError: (error: unknown) => handleActionError(error, "game.solutionFailed"),
  });

  const phaseText = [
    t("game.phaseText.prefix", { phase }),
    state?.round ? t("game.phaseText.game", { round: state.round }) : "",
    maxQuestions ? t("game.phaseText.questions", { current: questionCount, max: maxQuestions }) : "",
  ]
    .filter(Boolean)
    .join(" • ");
  const canAsk = phase === "QUESTIONING" && question.trim().length > 0;
  const canSubmitSolution = phase === "QUESTIONING" && solution.trim().length > 0;

  return (
    <GameRoomFrame
      connected={socket.connected}
      showReconnectAction={socket.showReconnectAction}
      onReconnect={socket.reconnect}
      gameId={gameId}
      phase={phase}
      showTransition={showTransition}
      tutorialId={`room-${gameId}`}
      tutorialSteps={["game.tutorial.turtle_soup.0", "game.tutorial.turtle_soup.1", "game.tutorial.turtle_soup.2"].map((key) => t(key))}
      title={room?.name || t("game.turtleTitle")}
      phaseText={phaseText}
      phaseEndsAt={state?.phaseEndsAt}
      aliveCount={alivePlayers.length}
      playerCount={players.length}
      headerExtra={<BookOpen className="h-4 w-4 text-violet-500" />}
      chatMessages={chatMessages}
      myPlayerId={state?.myPlayerId}
      onSendChat={(type, content) => {
        const sent = socket.sendChat(type, content);
        if (!sent) {
          toast.error(t("game.chat.sendFailed"));
        }
      }}
    >
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[1fr_320px]">
        <div className="space-y-4">
          <Card className="p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <div className="text-xs text-muted-foreground">{t("game.currentCase")}</div>
                <h3 className="text-xl font-semibold">{caseTitle}</h3>
              </div>
              <Badge variant={phase === "SETTLEMENT" ? "secondary" : "outline"}>{phase}</Badge>
            </div>
            <p className="mt-3 rounded-md border bg-slate-50 p-3 text-sm leading-6 text-slate-700">
              {surface || t("game.waitSurface")}
            </p>
          </Card>

          <Card className="p-4">
            <div className="mb-3 flex items-center gap-2 text-sm font-medium">
              <Lightbulb className="h-4 w-4 text-amber-500" />
              {t("game.confirmedClues")}
            </div>
            {knownClues.length ? (
              <div className="flex flex-wrap gap-2">
                {knownClues.map((clue) => (
                  <Badge key={clue} variant="secondary" className="max-w-full whitespace-normal text-left">
                    {clue}
                  </Badge>
                ))}
              </div>
            ) : (
              <div className="text-sm text-muted-foreground">{t("game.noClues")}</div>
            )}
          </Card>

          <Card className="p-4">
            <div className="mb-3 flex items-center gap-2 text-sm font-medium">
              <HelpCircle className="h-4 w-4 text-blue-500" />
              {t("game.qaHistory")}
            </div>
            <div className="space-y-2">
              {qaHistory.map((item, index) => (
                <div key={`${index}-${item.question}`} className="rounded-md border bg-white p-3 text-sm">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant={item.aiGenerated ? "secondary" : "outline"}>{item.aiGenerated ? t("game.aiPlayer") : t("game.player")}</Badge>
                    <span className="font-medium">{item.question}</span>
                  </div>
                  <div className="mt-2 text-slate-700">{t("game.hostReply", { answer: item.answer })}</div>
                </div>
              ))}
              {!qaHistory.length && <div className="rounded-md border border-dashed p-4 text-sm text-muted-foreground">{t("game.noQa")}</div>}
            </div>
          </Card>

          {phase === "SETTLEMENT" && state && (
            <Card className="space-y-3 p-4">
              <div className="flex items-center gap-2 text-sm font-medium">
                <Trophy className="h-4 w-4 text-emerald-500" />
                {t("game.solutionTitle")}
              </div>
              <p className="rounded-md bg-emerald-50 p-3 text-sm leading-6 text-emerald-900">{revealedSolution || t("game.noSolution")}</p>
              {hostVerdict && <p className="text-sm text-muted-foreground">{hostVerdict}</p>}
              <SettlementPanel gameId={gameId} state={state} userKey={userKey} />
            </Card>
          )}

          <GameLogPanel logs={state?.logs} emptyText={t("game.logEmpty.turtle")} />
        </div>

        <div className="space-y-4">
          <Card className="p-4">
            <PlayerGrid
              players={players}
              myPlayerId={state?.myPlayerId}
              phase={phase}
              votingPhase="NONE"
              speakingPhase="NONE"
              onSelectPlayer={() => undefined}
            />
          </Card>

          <Card className="space-y-3 p-4">
            {phase === "WAITING" && (
              <>
                <p className="text-sm text-muted-foreground">{t("game.waitSurfaceHost")}</p>
                <Button data-testid="game-start-btn" onClick={startGame} disabled={startMutation.isPending} className="w-full">
                  <Play className="mr-2 h-4 w-4" /> {t("lobby.startGame")}
                </Button>
              </>
            )}
            {phase === "QUESTIONING" && (
              <>
                <div className="space-y-2">
                  <div className="text-sm font-medium">{t("game.askHost")}</div>
                  <Textarea data-testid="turtle-question-input" value={question} onChange={(event) => setQuestion(event.target.value)} placeholder={t("game.questionPlaceholder")} />
                  <Button data-testid="turtle-question-submit-btn" className="w-full" disabled={!canAsk || askMutation.isPending} onClick={() => askMutation.mutate()}>
                    <Send className="mr-2 h-4 w-4" /> {t("game.ask")}
                  </Button>
                </div>
                <div className="space-y-2">
                  <div className="text-sm font-medium">{t("game.submitSolutionTitle")}</div>
                  <Textarea data-testid="turtle-solution-input" value={solution} onChange={(event) => setSolution(event.target.value)} placeholder={t("game.solutionPlaceholder")} />
                  <Button data-testid="turtle-solution-submit-btn" variant="secondary" className="w-full" disabled={!canSubmitSolution || solutionMutation.isPending} onClick={() => solutionMutation.mutate()}>
                    {t("game.submitSolution")}
                  </Button>
                </div>
              </>
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
      </div>
    </GameRoomFrame>
  );
};

export default TurtleSoupRoom;
