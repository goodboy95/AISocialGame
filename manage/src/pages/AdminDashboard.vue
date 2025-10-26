<template>
  <div class="dashboard">
    <transition name="fade" mode="out-in">
      <section v-if="!activeSection" key="overview" class="dashboard__overview">
        <el-row :gutter="24">
          <el-col v-for="section in sections" :key="section.key" :xs="24" :sm="12" :lg="8">
            <el-card class="dashboard__card" shadow="hover" @click="openSection(section.key)">
              <div class="dashboard__card-title">{{ section.title }}</div>
              <p class="dashboard__card-desc">{{ section.description }}</p>
              <el-button type="primary" link>进入</el-button>
            </el-card>
          </el-col>
        </el-row>
      </section>
      <section v-else-if="activeSection === 'basic'" key="basic" class="dashboard__detail">
        <div class="dashboard__detail-header">
          <el-button text @click="goBack">返回</el-button>
          <h2>基础功能管理</h2>
        </div>
        <el-result icon="info" title="开发中..." sub-title="基础功能管理模块正在建设中，请稍后再试。" />
      </section>
      <section v-else-if="activeSection === 'ai-models'" key="ai" class="dashboard__detail">
        <ai-model-config-manager @back="goBack" />
      </section>
      <section v-else key="games" class="dashboard__detail">
        <game-config-panel @back="goBack" />
      </section>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import AiModelConfigManager from '../components/AiModelConfigManager.vue';
import GameConfigPanel from '../components/GameConfigPanel.vue';

type SectionKey = 'basic' | 'ai-models' | 'games';

const sections: Array<{ key: SectionKey; title: string; description: string }> = [
  {
    key: 'basic',
    title: '基础功能管理',
    description: '管理平台基础能力配置与全局功能开关。'
  },
  {
    key: 'ai-models',
    title: 'AI 模型配置管理',
    description: '维护 AI 大模型的接入地址与鉴权信息。'
  },
  {
    key: 'games',
    title: '游戏配置',
    description: '管理“谁是卧底”“狼人杀”等游戏的玩法配置。'
  }
];

const activeSection = ref<SectionKey | null>(null);

function openSection(section: SectionKey) {
  activeSection.value = section;
}

function goBack() {
  activeSection.value = null;
}
</script>

<style scoped lang="scss">
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.dashboard__overview {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.dashboard__card {
  cursor: pointer;
  min-height: 180px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  border: 1px solid rgba(31, 47, 61, 0.08);
}

.dashboard__card-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 8px;
}

.dashboard__card-desc {
  color: #606266;
  line-height: 1.6;
  flex: 1;
}

.dashboard__detail {
  background: #fff;
  padding: 24px;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
  border: 1px solid rgba(31, 47, 61, 0.08);
}

.dashboard__detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
