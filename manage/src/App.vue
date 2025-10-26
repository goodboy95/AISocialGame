<template>
  <div class="app-shell">
    <header class="app-shell__header">
      <div class="app-shell__title">AI Social Game 管理后台</div>
      <div v-if="auth.profile" class="app-shell__profile">
        <el-tag type="success" size="small">管理员</el-tag>
        <span class="app-shell__profile-name">{{ displayName }}</span>
      </div>
    </header>
    <main class="app-shell__main">
      <el-skeleton v-if="!auth.initialized" :rows="6" animated />
      <el-result
        v-else-if="auth.error"
        icon="warning"
        title="无法访问管理后台"
      :sub-title="auth.error"
      class="app-shell__result"
    >
      <template #extra>
          <el-button type="primary" @click="auth.resetAndRetry()">重新检测</el-button>
      </template>
    </el-result>
      <router-view v-else />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useAuthStore } from './store/auth';

const auth = useAuthStore();
const displayName = computed(() => auth.profile?.display_name || auth.profile?.username || '');

onMounted(() => {
  auth.initialize();
});
</script>

<style scoped lang="scss">
.app-shell {
  min-height: 100vh;
  background: #f5f7fa;
  display: flex;
  flex-direction: column;
}

.app-shell__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 32px;
  background: #1f2f3d;
  color: #fff;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
}

.app-shell__title {
  font-size: 20px;
  font-weight: 600;
}

.app-shell__profile {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}

.app-shell__profile-name {
  font-weight: 500;
}

.app-shell__main {
  flex: 1;
  padding: 24px 32px 48px;
}

.app-shell__result {
  margin-top: 80px;
}
</style>
