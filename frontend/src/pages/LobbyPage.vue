<template>
  <section class="lobby">
    <header class="lobby__header">
      <div>
        <h2>游戏大厅</h2>
        <p>浏览可加入的房间，或创建一个新的房间邀请好友。</p>
      </div>
      <el-button type="primary" @click="openCreateDialog">创建房间</el-button>
    </header>

    <el-form :inline="true" class="lobby__filters" @submit.prevent>
      <el-form-item label="搜索">
        <el-input v-model="filters.search" placeholder="输入房间名称" clearable @change="refresh" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" placeholder="全部" clearable @change="refresh">
          <el-option label="等待中" value="waiting" />
          <el-option label="进行中" value="in_progress" />
        </el-select>
      </el-form-item>
      <el-form-item label="房号加入">
        <el-input v-model="joinCode" placeholder="输入房间号" maxlength="12">
          <template #append>
            <el-button @click="handleJoinByCode" :disabled="!joinCode">加入</el-button>
          </template>
        </el-input>
      </el-form-item>
    </el-form>

    <el-empty description="暂时没有可加入的房间" v-if="!rooms.length && !loading" />
    <el-skeleton :loading="loading" animated :count="3" v-else-if="loading">
      <template #template>
        <el-card class="lobby__card-skeleton" />
      </template>
    </el-skeleton>
    <el-row :gutter="16" v-else>
      <el-col :span="8" v-for="room in rooms" :key="room.id">
        <el-card shadow="hover" class="lobby__card">
          <template #header>
            <div class="lobby__card-header">
              <span>{{ room.name }}</span>
              <el-tag size="small" :type="room.status === 'waiting' ? 'success' : 'warning'">{{ room.statusDisplay }}</el-tag>
            </div>
          </template>
          <p>房主：{{ room.owner.displayName }}</p>
          <p>房间号：<strong>{{ room.code }}</strong></p>
          <p>模式：{{ resolveGameLabel(room.engine) }}</p>
          <p>人数：{{ room.playerCount }}/{{ room.maxPlayers }}</p>
          <div class="lobby__card-actions">
            <el-button type="primary" plain size="small" @click="enterRoom(room.id)">加入房间</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="createDialogVisible" title="创建房间" width="420px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="房间名称">
          <el-input v-model="createForm.name" placeholder="给你的房间起个名字" />
        </el-form-item>
        <el-form-item label="游戏模式">
          <el-select v-model="createForm.engine" placeholder="选择游戏模式">
            <el-option v-for="option in gameModeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="人数上限">
          <el-input-number v-model="createForm.maxPlayers" :min="2" :max="12" />
        </el-form-item>
        <el-form-item label="是否私密">
          <el-switch v-model="createForm.isPrivate" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
        </span>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { ElMessage } from "element-plus";
import { notifyError } from "../services/notifications";

import { useRoomsStore } from "../store/rooms";
import { useAuthStore } from "../store/user";

const router = useRouter();
const roomsStore = useRoomsStore();
const authStore = useAuthStore();

const { rooms, loading } = storeToRefs(roomsStore);

const filters = reactive({
  search: "",
  status: ""
});

const joinCode = ref("");
const createDialogVisible = ref(false);
const creating = ref(false);
const createForm = reactive({
  name: "",
  maxPlayers: 8,
  isPrivate: false,
  engine: "undercover"
});

const gameModeOptions = [
  { value: "undercover", label: "谁是卧底" },
  { value: "werewolf", label: "狼人杀" }
];

onMounted(() => {
  refresh();
});

async function refresh() {
  await roomsStore.fetchRooms({ search: filters.search || undefined, status: filters.status || undefined });
}

function openCreateDialog() {
  if (!authStore.accessToken) {
    ElMessage.warning("请先登录后再创建房间");
    router.push({ name: "login" });
    return;
  }
  createDialogVisible.value = true;
}

async function submitCreate() {
  if (!createForm.name.trim()) {
    notifyError("房间名称不能为空");
    return;
  }
  creating.value = true;
  try {
    const room = await roomsStore.createRoom({ ...createForm });
    createDialogVisible.value = false;
    createForm.name = "";
    createForm.engine = "undercover";
    await enterRoom(room.id);
  } catch (error) {
    console.error(error);
  } finally {
    creating.value = false;
  }
}

function resolveGameLabel(engine: string): string {
  const found = gameModeOptions.find((item) => item.value === engine);
  return found ? found.label : engine;
}

async function enterRoom(roomId: number) {
  if (!authStore.accessToken) {
    ElMessage.warning("请先登录再加入房间");
    router.push({ name: "login" });
    return;
  }
  try {
    await roomsStore.joinRoom(roomId);
    router.push({ name: "room-detail", params: { id: roomId } });
  } catch (error) {
    console.error(error);
  }
}

async function handleJoinByCode() {
  if (!joinCode.value.trim()) {
    return;
  }
  if (!authStore.accessToken) {
    ElMessage.warning("请先登录再加入房间");
    router.push({ name: "login" });
    return;
  }
  try {
    const room = await roomsStore.joinRoomWithCode(joinCode.value.trim());
    router.push({ name: "room-detail", params: { id: room.id } });
    joinCode.value = "";
  } catch (error) {
    console.error(error);
  }
}
</script>

<style scoped>
.lobby {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.lobby__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.lobby__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  padding: 12px 0;
}

.lobby__card {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.lobby__card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.lobby__card-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.lobby__card-skeleton {
  height: 180px;
}
</style>
