<template>
  <section class="stats" v-loading="loading">
    <el-card class="stats__panel">
      <div class="stats__header">
        <div>
          <h2>{{ t("stats.title") }}</h2>
          <p class="stats__subtitle">{{ profile?.display_name ?? profile?.username }}</p>
        </div>
        <div class="stats__actions">
          <el-button size="small" @click="handleExport">{{ t("stats.export") }}</el-button>
          <el-button size="small" type="danger" plain @click="handleDelete">{{ t("stats.delete") }}</el-button>
        </div>
      </div>
      <el-descriptions :column="3" border>
        <el-descriptions-item :label="t('stats.ownedRooms')">
          {{ summary?.ownedRooms ?? 0 }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('stats.joinedRooms')">
          {{ summary?.joinedRooms ?? 0 }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('stats.lastJoined')">
          {{ lastJoined }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="stats__panel">
      <template #header>
        <div class="stats__table-title">{{ t("stats.joinedRooms") }}</div>
      </template>
      <el-empty v-if="!memberships.length" :description="t('stats.noData')" />
      <el-table v-else :data="memberships" stripe>
        <el-table-column prop="roomName" :label="t('stats.roomName')" min-width="180" />
        <el-table-column prop="status" :label="t('stats.status')" min-width="120" />
        <el-table-column prop="joinedAt" :label="t('stats.lastJoined')" min-width="180" />
        <el-table-column prop="aiStyleLabel" :label="t('room.aiStyle')" min-width="140" />
        <el-table-column prop="role" :label="t('room.ui.role')" min-width="120" />
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";

import { exportUserData, deleteAccount } from "../api/user";
import { useAuthStore } from "../store/user";
import { useMetaStore } from "../store/meta";

const { t } = useI18n();
const authStore = useAuthStore();
const metaStore = useMetaStore();

const loading = ref(false);
const exportPayload = ref<Awaited<ReturnType<typeof exportUserData>> | null>(null);

const profile = computed(() => exportPayload.value?.profile ?? authStore.profile);
const summary = computed(() => exportPayload.value?.statistics ?? null);
const memberships = computed(() => {
  const data = exportPayload.value?.memberships ?? [];
  return data.map((item) => ({
    ...item,
    joinedAt: new Intl.DateTimeFormat(undefined, {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date(item.joinedAt)),
    status: item.status,
    aiStyleLabel: metaStore.styleLabel(item.aiStyle),
  }));
});
const lastJoined = computed(() => memberships.value[0]?.joinedAt ?? "-");

async function loadExport() {
  loading.value = true;
  try {
    await authStore.loadProfile();
    await metaStore.loadAiStyles();
    exportPayload.value = await exportUserData();
  } finally {
    loading.value = false;
  }
}

async function handleExport() {
  if (!exportPayload.value) {
    return;
  }
  const blob = new Blob([JSON.stringify(exportPayload.value, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `ai-social-game-${Date.now()}.json`;
  link.click();
  URL.revokeObjectURL(url);
  ElMessage.success(t("stats.export"));
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm(t("stats.confirmDelete"), t("stats.delete"), {
      confirmButtonText: t("common.confirm"),
      cancelButtonText: t("common.cancel"),
      type: "warning",
    });
  } catch {
    return;
  }
  await deleteAccount();
  ElMessage.success(t("stats.delete"));
}

onMounted(() => {
  loadExport();
});
</script>

<style scoped lang="scss">
.stats {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.stats__panel {
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(31, 35, 41, 0.08);
}

.stats__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stats__subtitle {
  margin: 0;
  color: #80868b;
}

.stats__actions {
  display: flex;
  gap: 8px;
}

.stats__table-title {
  font-weight: 600;
}
</style>
