import { useTranslation } from "react-i18next";

interface PhaseTransitionProps {
  gameId?: string;
  phase?: string;
  visible: boolean;
}

interface PhaseConfig {
  icon: string;
  titleKey: string;
  subtitleKey: string;
  bgClass: string;
}

const phaseConfig: Record<string, Record<string, PhaseConfig>> = {
  undercover: {
    DESCRIPTION: { icon: "💬", titleKey: "game.phase.undercover.DESCRIPTION.title", subtitleKey: "game.phase.undercover.DESCRIPTION.subtitle", bgClass: "from-cyan-700 to-blue-900" },
    VOTING: { icon: "🗳️", titleKey: "game.phase.undercover.VOTING.title", subtitleKey: "game.phase.undercover.VOTING.subtitle", bgClass: "from-amber-600 to-red-700" },
    SETTLEMENT: { icon: "🎭", titleKey: "game.phase.undercover.SETTLEMENT.title", subtitleKey: "game.phase.undercover.SETTLEMENT.subtitle", bgClass: "from-violet-700 to-indigo-900" },
  },
  werewolf: {
    NIGHT: { icon: "🌙", titleKey: "game.phase.werewolf.NIGHT.title", subtitleKey: "game.phase.werewolf.NIGHT.subtitle", bgClass: "from-slate-800 to-black" },
    DAY_DISCUSS: { icon: "☀️", titleKey: "game.phase.werewolf.DAY_DISCUSS.title", subtitleKey: "game.phase.werewolf.DAY_DISCUSS.subtitle", bgClass: "from-yellow-500 to-orange-600" },
    DAY_VOTE: { icon: "⚖️", titleKey: "game.phase.werewolf.DAY_VOTE.title", subtitleKey: "game.phase.werewolf.DAY_VOTE.subtitle", bgClass: "from-rose-600 to-red-800" },
    SETTLEMENT: { icon: "⚔️", titleKey: "game.phase.werewolf.SETTLEMENT.title", subtitleKey: "game.phase.werewolf.SETTLEMENT.subtitle", bgClass: "from-purple-700 to-slate-900" },
  },
};

export const PhaseTransition = ({ gameId, phase, visible }: PhaseTransitionProps) => {
  const { t } = useTranslation();
  const config = phase && gameId ? phaseConfig[gameId]?.[phase] : undefined;
  if (!config || !visible) {
    return null;
  }

  return (
    <div className={`fixed inset-0 z-[70] flex items-center justify-center bg-gradient-to-b ${config.bgClass} transition-opacity duration-200`}>
      <div className="text-center text-white">
        <div className="mb-3 text-6xl">{config.icon}</div>
        <h2 className="text-3xl font-bold">{t(config.titleKey)}</h2>
        <p className="mt-1 text-white/85">{t(config.subtitleKey)}</p>
      </div>
    </div>
  );
};
