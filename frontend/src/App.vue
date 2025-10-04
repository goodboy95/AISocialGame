<template>
  <div class="layout" :class="`locale-${language}`">
    <el-container class="app-container">
      <el-header height="64px">
        <div class="brand">
          <div class="brand__title">{{ t("app.title") }}</div>
          <div class="brand__tagline">{{ t("app.tagline") }}</div>
        </div>
        <el-space size="large" class="nav-actions">
          <el-button type="primary" link @click="goTo('lobby')">{{ t("nav.lobby") }}</el-button>
          <el-button type="primary" link @click="goTo('stats')">{{ t("nav.stats") }}</el-button>
          <template v-if="!isLoggedIn">
            <el-button type="primary" link @click="goTo('login')">{{ t("nav.login") }}</el-button>
            <el-button type="primary" link @click="goTo('register')">{{ t("nav.register") }}</el-button>
          </template>
          <template v-else>
            <el-dropdown trigger="click">
              <span class="user-entry">
                <el-avatar
                  v-if="profile?.avatar"
                  :src="profile.avatar"
                  :size="32"
                  class="user-avatar"
                />
                <span class="user-name">{{ displayName }}</span>
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="goTo('stats')">{{ t("nav.stats") }}</el-dropdown-item>
                  <el-dropdown-item divided @click="handleLogout">{{ t("nav.logout") }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <el-select v-model="language" size="small" class="language-select" @change="handleLocaleChange">
            <el-option :label="t('common.chinese')" value="zh" />
            <el-option :label="t('common.english')" value="en" />
          </el-select>
        </el-space>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import { useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { ArrowDown } from "@element-plus/icons-vue";

import { setLocale, type SupportedLocale } from "./i18n";
import { useAuthStore } from "./store/user";

const authStore = useAuthStore();
const { profile } = storeToRefs(authStore);
const isLoggedIn = computed(() => Boolean(profile.value));
const displayName = computed(
  () => profile.value?.display_name || profile.value?.username || ""
);

const router = useRouter();
const { t, locale } = useI18n();

const language = computed({
  get: () => locale.value as SupportedLocale,
  set: (value: SupportedLocale) => {
    setLocale(value);
  },
});

function goTo(name: string) {
  router.push({ name });
}

function handleLocaleChange(value: SupportedLocale) {
  setLocale(value);
}

function handleLogout() {
  authStore.logout();
  router.push({ name: "login" });
}
</script>

<style scoped lang="scss">
.layout {
  min-height: 100vh;
  background: linear-gradient(180deg, #f0f6ff 0%, #ffffff 100%);
}

.app-container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px;
}

.brand {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-weight: 600;
}

.brand__title {
  font-size: 20px;
}

.brand__tagline {
  font-size: 12px;
  font-weight: 400;
  color: #80868b;
}

.nav-actions {
  align-items: center;
}

.language-select {
  width: 120px;
}

.user-entry {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  cursor: pointer;
  color: #1f2f3d;
}

.user-avatar {
  border: 1px solid rgba(31, 47, 61, 0.1);
}

.user-name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
