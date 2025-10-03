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
          <p>词语：<strong>{{ selfWordDisplay }}</strong></p>
          <p v-if="gameState?.word_pair?.topic">主题：{{ gameState.word_pair.topic }}</p>
          <el-alert v-if="gameState?.winner" :title="winnerDescription" type="success" show-icon />
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
              :class="['room__member', { 'room__member-host': player.isHost, 'room__member-current': currentSpeakerId === player.id }]"
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
            <el-result :icon="gameState.winner === 'civilian' ? 'success' : 'warning'" :title="winnerTitle">
              <template #sub-title>
                <span>{{ winnerDescription }}</span>
              </template>
            </el-result>
          </div>
          <template v-else>
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
              <p class="room__vote-summary">投票进度：{{ gameState.voteSummary.submitted }} / {{ gameState.voteSummary.required }}</p>
            </div>
            <div v-else-if="currentPhase === 'result'" class="room__phase-block">
              <el-alert title="本轮已结束，请等待房主开启下一轮（即将上线）" type="success" show-icon />
            </div>
          </template>
          <div v-if="gameState?.speeches?.length" class="room__speeches">
            <h4>发言记录</h4>
            <el-timeline>
              <el-timeline-item
                v-for="speech in gameState.speeches"
                :key="speech.timestamp + speech.player_id"
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

const room = computed(() => currentRoom.value);
const gameSession = computed(() => room.value?.gameSession ?? null);
const gameState = computed(() => gameSession.value?.state ?? null);
const currentPhase = computed(() => gameState.value?.phase ?? "preparing");
const currentSpeakerId = computed(() => gameState.value?.current_player_id ?? null);
const profile = computed(() => authStore.profile);
const selfPlayer = computed(() =>
  room.value?.players.find((player) => player.userId && player.userId === profile.value?.id) ?? null
);
const assignments = computed(() => gameState.value?.assignments ?? []);
const playerStatusMap = computed(() => {
  const map = new Map<number, { isAlive: boolean }>();
  assignments.value.forEach((assignment) => {
    map.set(assignment.playerId, { isAlive: assignment.isAlive });
  });
  return map;
});
const aliveAssignments = computed(() => assignments.value.filter((assignment) => assignment.isAlive));
const hasVoted = computed(() => Boolean(gameState.value?.voteSummary?.selfTarget));
const voteTarget = computed(() => gameState.value?.voteSummary?.selfTarget ?? null);

const phaseMap: Record<string, string> = {
  preparing: "准备阶段",
  speaking: "发言阶段",
  voting: "投票阶段",
  result: "结果结算",
  ended: "游戏结束"
};

const phaseDescription = computed(() => {
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
});

const phaseDisplay = computed(() => {
  if (room.value?.status === "waiting") {
    return "等待开始";
  }
  return phaseMap[currentPhase.value] ?? "未开始";
});
const currentSpeakerName = computed(() => {
  if (!currentSpeakerId.value) return "待定";
  const found = assignments.value.find((item) => item.playerId === currentSpeakerId.value);
  return found?.displayName ?? `玩家 #${currentSpeakerId.value}`;
});

const canSpeak = computed(
  () => currentPhase.value === "speaking" && selfPlayer.value && selfPlayer.value.id === currentSpeakerId.value
);

const selfAssignment = computed(() =>
  assignments.value.find((assignment) => assignment.playerId === selfPlayer.value?.id) ?? null
);

const selfRoleDisplay = computed(() => {
  if (!selfAssignment.value) {
    return "未分配";
  }
  const role = selfAssignment.value.role ?? "保密";
  return role === "civilian" ? "平民" : role === "undercover" ? "卧底" : role === "blank" ? "白板" : role;
});

const selfWordDisplay = computed(() => selfAssignment.value?.word ?? "等待分发");

const winnerTitle = computed(() => {
  if (!gameState.value?.winner) return "";
  return gameState.value.winner === "civilian" ? "平民获胜" : "卧底反杀";
});

const winnerDescription = computed(() => {
  if (!gameState.value?.winner) return "";
  return gameState.value.winner === "civilian" ? "卧底全部出局，本局胜利！" : "卧底人数已反超，平民失败。";
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

watch(currentPhase, (phase) => {
  if (phase !== "speaking") {
    speechInput.value = "";
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
  gap: 16px;
}

.room__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.room__layout {
  flex: 1;
}

.room__panel {
  height: 100%;
}

.room__panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.room__sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.room__members {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.room__member {
  padding: 8px;
  border-radius: 8px;
  background: #f5f7fa;
}

.room__member-current {
  border: 1px solid var(--el-color-primary);
}

.room__member-host {
  font-weight: 600;
}

.room__member-name {
  display: flex;
  align-items: center;
  gap: 6px;
}

.room__member-meta {
  margin-top: 4px;
  display: flex;
  gap: 8px;
  align-items: center;
}

.room__member-host-icon {
  color: #fadb14;
}

.room__game {
  display: flex;
  flex-direction: column;
}

.room__phase-desc {
  margin-bottom: 12px;
  color: #606266;
}

.room__phase-block {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.room__speak-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.room__vote-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.room__vote-summary {
  color: #606266;
}

.room__speeches {
  margin-top: 16px;
}

.room__chat {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.room__chat-history {
  flex: 1;
  overflow-y: auto;
  padding-right: 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 420px;
}

.room__chat-message {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 8px 12px;
}

.room__chat-message--system {
  background: transparent;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

.room__chat-time {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

.room__chat-input {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.room__chat-system {
  color: #909399;
}

.room__winner {
  margin: 16px 0;
}
</style>
