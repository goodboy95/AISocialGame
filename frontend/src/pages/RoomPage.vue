<template>
  <section class="room" v-if="room">
    <header class="room__header">
      <div>
        <h2>{{ room.name }}</h2>
        <p>房间号：<strong>{{ room.code }}</strong> · 房主：{{ room.owner.displayName }}</p>
      </div>
      <div class="room__actions">
        <el-tag :type="room.status === 'waiting' ? 'success' : 'warning'">{{ room.statusDisplay }}</el-tag>
        <el-button v-if="room.isOwner" type="primary" :disabled="room.status !== 'waiting'" @click="handleStart">开始游戏</el-button>
        <el-button @click="handleLeave">离开房间</el-button>
      </div>
    </header>
    <el-row :gutter="16" class="room__content">
      <el-col :span="8">
        <el-card class="room__panel">
          <template #header>
            <div class="room__panel-header">
              <span>成员列表</span>
              <el-tag size="small" :type="socketConnected ? 'success' : 'info'">{{ socketConnected ? "实时连接正常" : "离线" }}</el-tag>
            </div>
          </template>
          <ul class="room__members">
            <li v-for="player in room.players" :key="player.id" :class="{ 'room__member-host': player.isHost }">
              <div class="room__member-name">
                <el-icon v-if="player.isHost" class="room__member-host-icon"><Crown /></el-icon>
                <span>{{ player.displayName }}</span>
                <el-tag v-if="player.isAi" size="small" type="info">AI</el-tag>
              </div>
              <small>座位 {{ player.seatNumber }}</small>
            </li>
          </ul>
        </el-card>
      </el-col>
      <el-col :span="16">
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
import { Crown } from "@element-plus/icons-vue";
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

const room = computed(() => currentRoom.value);

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
    ElMessage.success("已开始游戏，等待游戏引擎接入");
  } catch (error) {
    console.error(error);
    ElMessage.error("当前无法开始游戏");
  }
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

.room__content {
  flex: 1;
}

.room__panel {
  height: 100%;
}

.room__panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.room__members {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.room__member-host {
  font-weight: 600;
}

.room__member-name {
  display: flex;
  align-items: center;
  gap: 6px;
}

.room__member-host-icon {
  color: #fadb14;
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
</style>
