<template>
  <div class="layout" :class="`locale-${language}`">
    <el-container class="app-container">
      <el-header height="64px">
        <div class="brand">
          <div class="brand__title">{{ t("app.title") }}</div>
          <div class="brand__tagline">{{ t("app.tagline") }}</div>
        </div>
        <el-space size="large">
          <el-button type="primary" link @click="goTo('login')">{{ t("nav.login") }}</el-button>
          <el-button type="primary" link @click="goTo('register')">{{ t("nav.register") }}</el-button>
          <el-button type="primary" link @click="goTo('lobby')">{{ t("nav.lobby") }}</el-button>
          <el-button type="primary" link @click="goTo('stats')">{{ t("nav.stats") }}</el-button>
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
import { useRouter } from "vue-router";
import { computed } from "vue";
import { useI18n } from "vue-i18n";

import { setLocale, type SupportedLocale } from "./i18n";

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

.language-select {
  width: 120px;
}
</style>
