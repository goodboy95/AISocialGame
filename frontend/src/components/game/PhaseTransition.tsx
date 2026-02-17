interface PhaseTransitionProps {
  gameId?: string;
  phase?: string;
  visible: boolean;
}

const phaseConfig: Record<string, Record<string, { icon: string; title: string; subtitle: string; bgClass: string }>> = {
  undercover: {
    DESCRIPTION: { icon: "💬", title: "描述阶段", subtitle: "用一句话描述你的词语", bgClass: "from-cyan-700 to-blue-900" },
    VOTING: { icon: "🗳️", title: "投票阶段", subtitle: "投出你认为的卧底", bgClass: "from-amber-600 to-red-700" },
    SETTLEMENT: { icon: "🎭", title: "结算阶段", subtitle: "身份揭示中", bgClass: "from-violet-700 to-indigo-900" },
  },
  werewolf: {
    NIGHT: { icon: "🌙", title: "夜晚阶段", subtitle: "请完成夜晚行动", bgClass: "from-slate-800 to-black" },
    DAY_DISCUSS: { icon: "☀️", title: "讨论阶段", subtitle: "分析线索并发言", bgClass: "from-yellow-500 to-orange-600" },
    DAY_VOTE: { icon: "⚖️", title: "投票阶段", subtitle: "选择要放逐的玩家", bgClass: "from-rose-600 to-red-800" },
    SETTLEMENT: { icon: "⚔️", title: "结算阶段", subtitle: "胜负已分", bgClass: "from-purple-700 to-slate-900" },
  },
};

export const PhaseTransition = ({ gameId, phase, visible }: PhaseTransitionProps) => {
  const config = phase && gameId ? phaseConfig[gameId]?.[phase] : undefined;
  if (!config || !visible) {
    return null;
  }

  return (
    <div className={`fixed inset-0 z-[70] flex items-center justify-center bg-gradient-to-b ${config.bgClass} transition-opacity duration-200`}>
      <div className="text-center text-white">
        <div className="mb-3 text-6xl">{config.icon}</div>
        <h2 className="text-3xl font-bold">{config.title}</h2>
        <p className="mt-1 text-white/85">{config.subtitle}</p>
      </div>
    </div>
  );
};
