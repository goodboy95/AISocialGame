<template>
  <section class="room" v-if="room">
    <header class="room__header">
      <div>
        <h2>{{ room.name }}</h2>
        <p>房间号：<strong>{{ room.code }}</strong> · 房主：{{ room.owner.displayName }}</p>
      </div>
      <div class="room__actions">
        <el-tag :type="socketConnected ? 'success' : 'info'">{{ socketConnected ? "实时在线" : "离线" }}</el-tag>
        <el-tag type="warning">{{ phaseDisplay }}</el-tag>
        <el-button v-if="room.status === 'waiting' && room.isOwner" type="primary" @click="handleStart">开始游戏</el-button>
        <el-button @click="handleLeave">离开房间</el-button>
      </div>
    </header>
    <el-row :gutter="16" class="room__layout">
      <el-col :span="6" class="room__sidebar">
        <el-card class="room__panel room__panel--info">
          <template #header>
            <span>我的身份</span>
          </template>
          <p>身份：<strong>{{ selfRoleDisplay }}</strong></p>
          <template v-if="isUndercover">
            <p>词语：<strong>{{ selfWordDisplay }}</strong></p>
            <p v-if="undercoverState?.word_pair?.topic">主题：{{ undercoverState.word_pair.topic }}</p>
          </template>
          <template v-else-if="isWerewolf">
            <p v-if="werewolfPrivateRoleName">阵营提示：<strong>{{ werewolfPrivateRoleName }}</strong></p>
            <p v-if="isWolf && werewolfAllyNames">同伴：{{ werewolfAllyNames }}</p>
            <p v-if="isSeerRole && seerLastResult">最新查验：{{ seerLastResult }}</p>
            <p v-if="isWitchRole">解药：{{ werewolfAntidoteAvailable ? "可用" : "已用尽" }} · 毒药：{{ werewolfPoisonAvailable ? "可用" : "已用尽" }}</p>
            <p v-if="werewolfPendingKillName">当前夜袭目标：{{ werewolfPendingKillName }}</p>
          </template>
          <el-alert v-if="winnerDescription" :title="winnerDescription" type="success" show-icon />
        </el-card>
        <el-card class="room__panel room__panel--players">
          <template #header>
            <div class="room__panel-header">
              <span>成员列表</span>
              <span>第 {{ gameSession?.round ?? room.currentRound }} 轮</span>
            </div>
          </template>
          <ul class="room__members">
            <li
              v-for="player in room.players"
              :key="player.id"
              :class="['room__member', { 'room__member-host': player.isHost, 'room__member-current': activeSpeakerId === player.id }]"
            >
              <div class="room__member-name">
                <el-icon v-if="player.isHost" class="room__member-host-icon"><Trophy /></el-icon>
                <span>{{ player.displayName }}</span>
                <el-tag v-if="player.isAi" size="small" type="info">AI</el-tag>
              </div>
              <div class="room__member-meta">
                <el-tag size="small" :type="playerStatusMap.get(player.id)?.isAlive ? 'success' : 'danger'">
                  {{ playerStatusMap.get(player.id)?.isAlive ? "存活" : "淘汰" }}
                </el-tag>
                <small>座位 {{ player.seatNumber }}</small>
              </div>
            </li>
          </ul>
        </el-card>
      </el-col>
      <el-col :span="10" class="room__game">
        <el-card class="room__panel room__panel--stage">
          <template #header>
            <div class="room__panel-header">
              <span>游戏阶段</span>
              <el-tag type="info">{{ phaseDisplay }}</el-tag>
            </div>
          </template>
          <p class="room__phase-desc">{{ phaseDescription }}</p>
          <div v-if="gameState?.winner" class="room__winner">
            <el-result :icon="winnerIcon" :title="winnerTitle">
              <template #sub-title>
                <span>{{ winnerDescription }}</span>
              </template>
            </el-result>
          </div>
          <template v-else>
            <template v-if="isUndercover">
              <div v-if="currentPhase === 'preparing'" class="room__phase-block">
                <el-button v-if="room.isOwner" type="primary" @click="handleReady">通知开始发言</el-button>
                <el-alert v-else title="等待房主发起首轮发言" type="info" show-icon />
              </div>
              <div v-else-if="currentPhase === 'speaking'" class="room__phase-block">
                <p>当前发言：<strong>{{ currentSpeakerName }}</strong></p>
                <div v-if="canSpeak" class="room__speak-form">
                  <el-input
                    v-model="speechInput"
                    type="textarea"
                    :rows="3"
                    maxlength="120"
                    show-word-limit
                    placeholder="描述你的词语特征，帮助队友找到卧底"
                  />
                  <el-button type="primary" :disabled="!speechInput.trim()" @click="handleSubmitSpeech">提交发言</el-button>
                </div>
                <el-alert v-else title="等待当前玩家完成发言" type="info" show-icon />
              </div>
              <div v-else-if="currentPhase === 'voting'" class="room__phase-block">
                <p>请选择你怀疑的玩家：</p>
                <div class="room__vote-grid">
                  <el-button
                    v-for="assignment in aliveAssignments"
                    :key="assignment.playerId"
                    :type="voteTarget === assignment.playerId ? 'primary' : 'default'"
                    :disabled="hasVoted || assignment.playerId === selfPlayer?.id"
                    @click="handleVote(assignment.playerId)"
                  >
                    {{ assignment.displayName }}
                  </el-button>
                </div>
                <p class="room__vote-summary">投票进度：{{ (gameState as any)?.voteSummary?.submitted ?? 0 }} / {{ (gameState as any)?.voteSummary?.required ?? aliveAssignments.length }}</p>
              </div>
              <div v-else-if="currentPhase === 'result'" class="room__phase-block">
                <el-alert title="本轮已结束，请等待房主开启下一轮（即将上线）" type="success" show-icon />
              </div>
            </template>
            <template v-else-if="isWerewolf">
              <div v-if="werewolfStage === 'night.wolves'" class="room__phase-block">
                <p>狼人请密谋选择夜袭目标。</p>
                <div v-if="isWolf">
                  <el-button
                    v-for="assignment in werewolfKillOptions"
                    :key="assignment.playerId"
                    :type="werewolfPendingKill === assignment.playerId ? 'primary' : 'default'"
                    @click="handleWolfTarget(assignment.playerId)"
                  >
                    {{ assignment.displayName }}
                  </el-button>
                  <p class="room__phase-tip">当前选择：{{ werewolfPendingKillName || "暂未指定" }}</p>
                </div>
                <el-alert v-else title="夜晚进行中，请等待狼人行动" type="info" show-icon />
              </div>
              <div v-else-if="werewolfStage === 'night.seer'" class="room__phase-block">
                <p>预言家可查验一名玩家身份。</p>
                <div v-if="isSeerRole">
                  <el-button
                    v-for="assignment in werewolfInspectOptions"
                    :key="assignment.playerId"
                    @click="handleSeerTarget(assignment.playerId)"
                  >
                    {{ assignment.displayName }}
                  </el-button>
                </div>
                <el-alert v-else title="夜晚进行中，请等待预言家行动" type="info" show-icon />
              </div>
              <div v-else-if="werewolfStage === 'night.witch'" class="room__phase-block">
                <p>女巫可选择使用解药或毒药。</p>
                <div v-if="isWitchRole">
                  <div class="room__witch-actions">
                    <el-button
                      :disabled="!werewolfAntidoteAvailable || !werewolfPendingKill"
                      type="primary"
                      @click="handleWitchSave"
                    >
                      使用解药拯救 {{ werewolfPendingKillName || '目标' }}
                    </el-button>
                    <div class="room__witch-poison">
                      <el-select
                        v-model="witchPoisonTarget"
                        placeholder="选择投毒目标"
                        :disabled="!werewolfPoisonAvailable || werewolfPoisonOptions.length === 0"
                        style="width: 160px"
                      >
                        <el-option
                          v-for="assignment in werewolfPoisonOptions"
                          :key="assignment.playerId"
                          :label="assignment.displayName"
                          :value="assignment.playerId"
                        />
                      </el-select>
                      <el-button
                        type="danger"
                        :disabled="!werewolfPoisonAvailable || !witchPoisonTarget"
                        @click="handleWitchPoison"
                      >
                        投毒
                      </el-button>
                    </div>
                  </div>
                  <el-button type="info" plain @click="handleWitchSkip">本轮跳过</el-button>
                </div>
                <el-alert v-else title="夜晚进行中，请等待女巫行动" type="info" show-icon />
              </div>
              <div v-else-if="werewolfStage === 'day.discussion'" class="room__phase-block">
                <p>当前发言：<strong>{{ currentSpeakerName }}</strong></p>
                <div v-if="canSpeak" class="room__speak-form">
                  <el-input
                    v-model="speechInput"
                    type="textarea"
                    :rows="3"
                    maxlength="160"
                    show-word-limit
                    placeholder="请根据夜间信息发言，推动阵营胜利"
                  />
                  <el-button type="primary" :disabled="!speechInput.trim()" @click="handleSubmitSpeech">提交发言</el-button>
                </div>
                <el-alert v-else title="等待当前玩家完成发言" type="info" show-icon />
              </div>
              <div v-else-if="werewolfStage === 'day.vote'" class="room__phase-block">
                <p>请选择你要投出的玩家：</p>
                <div class="room__vote-grid">
                  <el-button
                    v-for="assignment in aliveAssignments"
                    :key="assignment.playerId"
                    :type="voteTarget === assignment.playerId ? 'primary' : 'default'"
                    :disabled="hasVoted || assignment.playerId === selfPlayer?.id"
                    @click="handleVote(assignment.playerId)"
                  >
                    {{ assignment.displayName }}
                  </el-button>
                </div>
                <p class="room__vote-summary">投票进度：{{ (werewolfState?.voteSummary?.submitted ?? 0) }} / {{ werewolfState?.voteSummary?.required ?? aliveAssignments.length }}</p>
              </div>
              <div v-else class="room__phase-block">
                <el-alert title="请关注系统提示，等待下一阶段" type="info" show-icon />
              </div>
            </template>
          </template>
          <div v-if="speeches.length" class="room__speeches">
            <h4>发言记录</h4>
            <el-timeline>
              <el-timeline-item
                v-for="speech in speeches"
                :key="speech.timestamp + '-' + speech.player_id"
                :timestamp="formatTime(speech.timestamp)"
              >
                <strong>{{ resolvePlayerName(speech.player_id) }}</strong>：{{ speech.content }}
              </el-timeline-item>
            </el-timeline>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="room__chat">
          <template #header>
            <div class="room__panel-header">
              <span>实时聊天</span>
            </div>
          </template>
          <div ref="chatContainer" class="room__chat-history">
            <div v-for="message in messages" :key="message.id" :class="['room__chat-message', `room__chat-message--${message.type}`]">
              <template v-if="message.type === 'chat'">
                <strong>{{ message.sender?.displayName }}</strong>
                <span class="room__chat-time">{{ formatTime(message.timestamp) }}</span>
                <p>{{ message.content }}</p>
              </template>
              <template v-else>
                <span class="room__chat-system">{{ message.content }}</span>
              </template>
            </div>
          </div>
          <div class="room__chat-input">
            <el-input
              v-model="messageInput"
              type="textarea"
              :rows="2"
              placeholder="输入聊天内容，按下 Enter 发送"
              @keyup.enter.exact.prevent="handleSend"
            />
            <el-button type="primary" :disabled="!messageInput.trim()" @click="handleSend">发送</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </section>
  <el-empty description="正在加载房间信息" v-else />
</template>

<script setup lang="ts">
import { Trophy } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";

import type {
  UndercoverAssignmentView,
  UndercoverStateView,
  WerewolfAssignmentView,
  WerewolfPrivateInfo,
  WerewolfStateView
} from "../types/rooms";
import { useRoomsStore } from "../store/rooms";
import { useAuthStore } from "../store/user";

const route = useRoute();
const router = useRouter();
const roomsStore = useRoomsStore();
const authStore = useAuthStore();

const { currentRoom, messages, socketConnected } = storeToRefs(roomsStore);
const messageInput = ref("");
const chatContainer = ref<HTMLDivElement | null>(null);
const speechInput = ref("");
const witchPoisonTarget = ref<number | null>(null);

const room = computed(() => currentRoom.value);
const gameSession = computed(() => room.value?.gameSession ?? null);
const gameEngine = computed(() => gameSession.value?.engine ?? "undercover");
const isUndercover = computed(() => gameEngine.value === "undercover");
const isWerewolf = computed(() => gameEngine.value === "werewolf");

const undercoverState = computed<UndercoverStateView | null>(() =>
  isUndercover.value ? ((gameSession.value?.state as UndercoverStateView | undefined) ?? null) : null
);
const werewolfState = computed<WerewolfStateView | null>(() =>
  isWerewolf.value ? ((gameSession.value?.state as WerewolfStateView | undefined) ?? null) : null
);
const gameState = computed(() => undercoverState.value ?? werewolfState.value ?? null);

const currentPhase = computed(() => gameState.value?.phase ?? "preparing");
const werewolfStage = computed(() => (isWerewolf.value ? werewolfState.value?.stage ?? "" : ""));
const activeSpeakerId = computed(() =>
  (isUndercover.value ? undercoverState.value?.current_player_id : werewolfState.value?.current_player_id) ?? null
);

const profile = computed(() => authStore.profile);
const selfPlayer = computed(() =>
  room.value?.players.find((player) => player.userId && player.userId === profile.value?.id) ?? null
);

const assignments = computed<(UndercoverAssignmentView | WerewolfAssignmentView)[]>(() => {
  if (isUndercover.value) {
    return undercoverState.value?.assignments ?? [];
  }
  if (isWerewolf.value) {
    return werewolfState.value?.assignments ?? [];
  }
  return [];
});

const playerStatusMap = computed(() => {
  const map = new Map<number, { isAlive: boolean }>();
  assignments.value.forEach((assignment) => {
    map.set(assignment.playerId, { isAlive: assignment.isAlive });
  });
  return map;
});

const aliveAssignments = computed(() => assignments.value.filter((assignment) => assignment.isAlive));
const speeches = computed(() => (gameState.value && Array.isArray((gameState.value as any).speeches) ? (gameState.value as any).speeches : []));
const hasVoted = computed(() => Boolean((gameState.value as any)?.voteSummary?.selfTarget));
const voteTarget = computed(() => (gameState.value as any)?.voteSummary?.selfTarget ?? null);

const phaseMap: Record<string, string> = {
  preparing: "准备阶段",
  speaking: "发言阶段",
  voting: "投票阶段",
  result: "结果结算",
  ended: "游戏结束",
  night: "夜晚阶段",
  day: "白天阶段"
};

const werewolfStageMap: Record<string, string> = {
  "night.wolves": "夜晚 · 狼人行动",
  "night.seer": "夜晚 · 预言家查验",
  "night.witch": "夜晚 · 女巫抉择",
  "day.discussion": "白天 · 发言讨论",
  "day.vote": "白天 · 公投决议",
  "day.result": "白天 · 结算"
};

const phaseDisplay = computed(() => {
  if (room.value?.status === "waiting") {
    return "等待开始";
  }
  if (isUndercover.value) {
    return phaseMap[currentPhase.value] ?? "游戏进行中";
  }
  if (isWerewolf.value) {
    return werewolfStageMap[werewolfStage.value] ?? (phaseMap[currentPhase.value] ?? "游戏进行中");
  }
  return "未开始";
});

const phaseDescription = computed(() => {
  if (isUndercover.value) {
    switch (currentPhase.value) {
      case "speaking":
        return "请按顺序描述你的词语，不要暴露身份";
      case "voting":
        return "根据所有发言，选择你怀疑的卧底";
      case "result":
        return "系统正在结算本轮结果";
      case "preparing":
        return "等待房主开启第一轮发言";
      default:
        return "游戏流程进行中";
    }
  }
  if (isWerewolf.value) {
    switch (werewolfStage.value) {
      case "night.wolves":
        return isWolf.value ? "请选择一名目标，夜里发动袭击" : "夜晚进行中，请留意白天公告";
      case "night.seer":
        return isSeerRole.value ? "查验一名玩家身份，注意保密" : "请等待预言家完成查验";
      case "night.witch":
        return isWitchRole.value ? "你可选择救人或投毒，谨慎决策" : "请等待女巫的操作";
      case "day.discussion":
        return "请依次发言，根据夜晚线索推理身份";
      case "day.vote":
        return "公开投票淘汰可疑玩家";
      default:
        return currentPhase.value === "night" ? "夜幕笼罩，静待天明" : "白天行动阶段";
    }
  }
  return "游戏流程进行中";
});

const currentSpeakerName = computed(() => {
  if (!activeSpeakerId.value) return "待定";
  const found = assignments.value.find((item) => item.playerId === activeSpeakerId.value);
  return found?.displayName ?? `玩家 #${activeSpeakerId.value}`;
});

const isSpeakingStage = computed(() =>
  (isUndercover.value && currentPhase.value === "speaking") ||
  (isWerewolf.value && werewolfStage.value === "day.discussion")
);

const canSpeak = computed(() =>
  isSpeakingStage.value && selfPlayer.value && selfPlayer.value.id === activeSpeakerId.value
);

const selfAssignment = computed(() =>
  assignments.value.find((assignment) => assignment.playerId === selfPlayer.value?.id) ?? null
);

const roleNameMap = new Map([
  ["civilian", "平民"],
  ["undercover", "卧底"],
  ["blank", "白板"],
  ["villager", "村民"],
  ["werewolf", "狼人"],
  ["seer", "预言家"],
  ["witch", "女巫"]
]);

const selfRoleDisplay = computed(() => {
  if (!selfAssignment.value) {
    return "未分配";
  }
  if (!selfAssignment.value.role) {
    return "保密";
  }
  return roleNameMap.get(selfAssignment.value.role) ?? selfAssignment.value.role;
});

const selfWordDisplay = computed(() => (isUndercover.value ? (selfAssignment.value as UndercoverAssignmentView | null)?.word ?? "等待分发" : "--"));

const werewolfPrivate = computed<WerewolfPrivateInfo>(() => (werewolfState.value?.private as WerewolfPrivateInfo) ?? { role: null });
const werewolfPrivateRoleName = computed(() => {
  const role = werewolfPrivate.value.role;
  if (!role) return "";
  return roleNameMap.get(role) ?? role;
});
const werewolfAllies = computed(() => werewolfPrivate.value.wolves?.allies ?? []);
const werewolfOtherWolves = computed(() =>
  werewolfAllies.value.filter((ally) => ally.playerId !== selfPlayer.value?.id)
);
const werewolfAllyNames = computed(() => werewolfOtherWolves.value.map((ally) => ally.displayName).join("、"));
const werewolfPendingKill = computed(() => werewolfPrivate.value.wolves?.selectedTarget ?? null);
const werewolfPendingKillName = computed(() => (werewolfPendingKill.value ? resolvePlayerName(werewolfPendingKill.value) : ""));
const werewolfAntidoteAvailable = computed(() => Boolean(werewolfPrivate.value.witch?.antidoteAvailable));
const werewolfPoisonAvailable = computed(() => Boolean(werewolfPrivate.value.witch?.poisonAvailable));
const werewolfKillOptions = computed(() => {
  const wolfIds = new Set(werewolfAllies.value.map((ally) => ally.playerId));
  return aliveAssignments.value.filter((assignment) => !wolfIds.has(assignment.playerId));
});
const werewolfInspectOptions = computed(() =>
  aliveAssignments.value.filter((assignment) => assignment.playerId !== selfPlayer.value?.id)
);
const werewolfPoisonOptions = computed(() =>
  aliveAssignments.value.filter((assignment) => assignment.playerId !== selfPlayer.value?.id)
);
const seerLastResult = computed(() => {
  if (!werewolfPrivate.value.seer?.lastResult) {
    return "";
  }
  const raw = werewolfPrivate.value.seer.lastResult as Record<string, any>;
  const playerId = raw.player_id ?? raw.playerId;
  const role = raw.role ?? "未知";
  const name = playerId ? resolvePlayerName(playerId) : "未知";
  return `${name} → ${roleNameMap.get(role) ?? role}`;
});

const isWolf = computed(() => werewolfPrivate.value.role === "werewolf");
const isSeerRole = computed(() => werewolfPrivate.value.role === "seer");
const isWitchRole = computed(() => werewolfPrivate.value.role === "witch");

const winnerIcon = computed(() => {
  const winner = (gameState.value as any)?.winner;
  if (!winner) return "info";
  if (isUndercover.value) {
    return winner === "civilian" ? "success" : "warning";
  }
  if (isWerewolf.value) {
    return winner === "villager" ? "success" : "warning";
  }
  return "info";
});

const winnerTitle = computed(() => {
  const winner = (gameState.value as any)?.winner;
  if (!winner) return "";
  if (isUndercover.value) {
    return winner === "civilian" ? "平民获胜" : "卧底反杀";
  }
  if (isWerewolf.value) {
    return winner === "villager" ? "好人阵营胜利" : "狼人阵营胜利";
  }
  return "";
});

const winnerDescription = computed(() => {
  const winner = (gameState.value as any)?.winner;
  if (!winner) return "";
  if (isUndercover.value) {
    return winner === "civilian" ? "卧底全部出局，本局胜利！" : "卧底人数已反超，平民失败。";
  }
  if (isWerewolf.value) {
    return winner === "villager" ? "好人阵营成功守护了村庄。" : "狼人占据上风，黑夜降临。";
  }
  return "";
});

onMounted(async () => {
  const roomId = Number(route.params.id);
  if (Number.isNaN(roomId)) {
    ElMessage.error("房间地址不正确");
    router.push({ name: "lobby" });
    return;
  }
  if (!authStore.accessToken) {
    ElMessage.warning("请先登录后再访问房间");
    router.push({ name: "login" });
    return;
  }
  try {
    const detail = await roomsStore.loadRoomDetail(roomId);
    if (!detail.isMember) {
      await roomsStore.joinRoom(roomId);
    }
    roomsStore.resetMessages();
    roomsStore.connectSocket(roomId);
  } catch (error) {
    console.error(error);
    ElMessage.error("房间不存在或暂时不可用");
    router.push({ name: "lobby" });
  }
});

onBeforeUnmount(() => {
  roomsStore.disconnectSocket();
  roomsStore.resetMessages();
});

watch(
  messages,
  async () => {
    await nextTick();
    const container = chatContainer.value;
    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  },
  { deep: true }
);

watch(isSpeakingStage, (speaking) => {
  if (!speaking) {
    speechInput.value = "";
  }
});

watch(werewolfStage, (stage) => {
  if (stage !== "night.witch") {
    witchPoisonTarget.value = null;
  }
});

function formatTime(timestamp: string) {
  return new Date(timestamp).toLocaleTimeString();
}

async function handleSend() {
  const content = messageInput.value.trim();
  if (!content) {
    return;
  }
  roomsStore.sendChat(content);
  messageInput.value = "";
}

async function handleLeave() {
  if (!room.value) {
    return;
  }
  try {
    await ElMessageBox.confirm("确定要离开当前房间吗？", "提示", { type: "warning" });
  } catch {
    return;
  }
  try {
    await roomsStore.leaveRoom(room.value.id);
    roomsStore.disconnectSocket();
    router.push({ name: "lobby" });
    ElMessage.success("已离开房间");
  } catch (error) {
    console.error(error);
    ElMessage.error("离开房间失败，请稍后再试");
  }
}

async function handleStart() {
  if (!room.value) {
    return;
  }
  try {
    await roomsStore.startRoom(room.value.id);
    ElMessage.success("已开始游戏，正在分配身份");
  } catch (error) {
    console.error(error);
    ElMessage.error("当前无法开始游戏");
  }
}

function handleReady() {
  roomsStore.sendGameEvent("ready");
}

function handleSubmitSpeech() {
  const content = speechInput.value.trim();
  if (!content) {
    ElMessage.warning("请先输入发言内容");
    return;
  }
  roomsStore.sendGameEvent("submit_speech", { content });
  speechInput.value = "";
}

function handleVote(targetId: number) {
  if (hasVoted.value) {
    ElMessage.info("已完成投票，等待其他玩家");
    return;
  }
  roomsStore.sendGameEvent("submit_vote", { target_id: targetId });
}

function handleWolfTarget(targetId: number) {
  roomsStore.sendGameEvent("submit_wolf_target", { target_id: targetId });
}

function handleSeerTarget(targetId: number) {
  roomsStore.sendGameEvent("submit_seer_target", { target_id: targetId });
}

function handleWitchSave() {
  if (!werewolfPendingKill.value) {
    ElMessage.info("当前没有需要拯救的目标");
    return;
  }
  roomsStore.sendGameEvent("submit_witch_action", { use_antidote: true });
}

function handleWitchPoison() {
  if (!witchPoisonTarget.value) {
    ElMessage.warning("请选择投毒目标");
    return;
  }
  roomsStore.sendGameEvent("submit_witch_action", { use_poison: true, target_id: witchPoisonTarget.value });
  witchPoisonTarget.value = null;
}

function handleWitchSkip() {
  roomsStore.sendGameEvent("submit_witch_action", {});
}

function resolvePlayerName(playerId: number) {
  const assignment = assignments.value.find((item) => item.playerId === playerId);
  if (assignment) {
    return assignment.displayName;
  }
  const fallback = room.value?.players.find((player) => player.id === playerId);
  return fallback?.displayName ?? `玩家 #${playerId}`;
}
</script>

<style scoped>
.room {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.room__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--el-color-white);
  padding: 16px;
  border-radius: 8px;
  box-shadow: var(--el-box-shadow-light);
}

.room__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.room__layout {
  width: 100%;
}

.room__panel {
  margin-bottom: 16px;
}

.room__panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.room__members {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.room__member {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 6px;
  background: #f5f7fa;
}

.room__member-host {
  border: 1px solid var(--el-color-primary);
}

.room__member-current {
  background: rgba(64, 158, 255, 0.1);
}

.room__member-name {
  display: flex;
  align-items: center;
  gap: 6px;
}

.room__member-host-icon {
  color: var(--el-color-warning);
}

.room__member-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.room__phase-desc {
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
}

.room__phase-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.room__speak-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.room__vote-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.room__vote-summary {
  color: var(--el-text-color-secondary);
}

.room__speeches {
  margin-top: 16px;
}

.room__chat {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.room__chat-history {
  max-height: 420px;
  overflow-y: auto;
  margin-bottom: 12px;
  padding-right: 4px;
}

.room__chat-message {
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.room__chat-message:last-child {
  border-bottom: none;
}

.room__chat-message--system {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.room__chat-time {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.room__chat-input {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.room__winner {
  margin: 16px 0;
}

.room__phase-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.room__witch-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.room__witch-poison {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
